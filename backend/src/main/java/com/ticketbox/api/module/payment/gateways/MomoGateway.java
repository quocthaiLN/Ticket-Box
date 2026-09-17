package com.ticketbox.api.module.payment.gateways;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.payment.domain.dtos.MomoCreatePaymentRequest;
import com.ticketbox.api.module.payment.domain.dtos.MomoCreatePaymentResponse;
import com.ticketbox.api.module.payment.domain.dtos.CallbackHandlingResult;
import com.ticketbox.api.module.payment.domain.dtos.GatewayCallback;
import com.ticketbox.api.module.payment.domain.dtos.PaymentCallbackResponse;
import com.ticketbox.api.module.payment.domain.dtos.PaymentGatewayRequest;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MomoGateway implements PaymentGatewayStrategy {

    private static final String REQUEST_TYPE = "captureWallet";
    private static final String LANGUAGE = "vi";
    private static final long MINIMUM_AMOUNT = 1_000L;

    private final RestClient restClient;
    private final String createUrl;
    private final String partnerCode;
    private final String accessKey;
    private final String secretKey;
    private final String redirectUrl;
    private final String ipnUrl;

    @Autowired
    public MomoGateway(
            @Value("${app.payment.momo.momo-url}") String createUrl,
            @Value("${app.payment.momo.partner-code}") String partnerCode,
            @Value("${app.payment.momo.access-key}") String accessKey,
            @Value("${app.payment.momo.secret-key}") String secretKey,
            @Value("${app.payment.momo.redirect-url}") String redirectUrl,
            @Value("${app.payment.momo.ipn-url}") String ipnUrl) {
        this(RestClient.create(), createUrl, partnerCode, accessKey, secretKey, redirectUrl, ipnUrl);
    }

    MomoGateway(
            RestClient restClient,
            String createUrl,
            String partnerCode,
            String accessKey,
            String secretKey,
            String redirectUrl,
            String ipnUrl) {
        this.restClient = restClient;
        this.createUrl = createUrl;
        this.partnerCode = partnerCode;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.redirectUrl = redirectUrl;
        this.ipnUrl = ipnUrl;
    }

    @Override
    public String createPaymentUrl(PaymentGatewayRequest request) {
        validateRequest(request);

        String orderId = request.txnRef();
        String requestId = UUID.randomUUID().toString();
        String orderInfo = request.orderInfo() == null || request.orderInfo().isBlank()
                ? "Payment for order " + request.orderId()
                : request.orderInfo();
        long amount = request.amount().longValueExact();
        String signature = createSignature(partnerCode, requestId, amount, orderId, orderInfo);
        MomoCreatePaymentRequest body = new MomoCreatePaymentRequest(
                partnerCode,
                requestId,
                amount,
                orderId,
                orderInfo,
                redirectUrl,
                ipnUrl,
                REQUEST_TYPE,
                "",
                LANGUAGE,
                signature);

        MomoCreatePaymentResponse response;
        try {
            response = restClient.post()
                    .uri(createUrl)
                    .body(body)
                    .retrieve()
                    .body(MomoCreatePaymentResponse.class);
        } catch (RestClientException exception) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "MOMO_CREATE_FAILED",
                    "MoMo payment service is unavailable");
        }

        if (response == null || response.resultCode() == null || response.resultCode() != 0
                || response.payUrl() == null || response.payUrl().isBlank()) {
            throw new AppException(HttpStatus.BAD_GATEWAY, "MOMO_CREATE_FAILED",
                    "MoMo did not create a payment URL");
        }
        return response.payUrl();
    }

    @Override
    public String transactionReference(UUID paymentId) {
        return paymentId.toString();
    }

    @Override
    public GatewayCallback verifyCallback(Map<String, String> params) {
        Map<String, String> callback = params == null ? Map.of() : params;
        String signature = callback.get("signature");
        boolean signatureValid = partnerCode.equals(callback.get("partnerCode"))
                && isValidSignature(signature, callbackSignatureData(callback));

        return new GatewayCallback(
                signatureValid,
                paymentId(callback.get("orderId")),
                amount(callback.get("amount")),
                callback.get("transId"),
                targetStatus(callback.get("resultCode")),
                failureReason(callback.get("resultCode")),
                withoutSignature(callback));
    }

    @Override
    public Optional<PaymentCallbackResponse> responseFor(CallbackHandlingResult result) {
        return Optional.empty();
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.MOMO;
    }

    private Optional<UUID> paymentId(String orderId) {
        try {
            return Optional.of(UUID.fromString(orderId));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> amount(String value) {
        try {
            return Optional.of(new BigDecimal(value).setScale(0));
        } catch (NumberFormatException | NullPointerException | ArithmeticException exception) {
            return Optional.empty();
        }
    }

    private Optional<PaymentStatus> targetStatus(String resultCode) {
        if ("0".equals(resultCode)) {
            return Optional.of(PaymentStatus.SUCCEEDED);
        }
        if ("1000".equals(resultCode) || "7000".equals(resultCode) || "7002".equals(resultCode)) {
            return Optional.of(PaymentStatus.PENDING);
        }
        return Optional.of(PaymentStatus.FAILED);
    }

    private Optional<String> failureReason(String resultCode) {
        return "0".equals(resultCode) || "1000".equals(resultCode) || "7000".equals(resultCode)
                || "7002".equals(resultCode)
                        ? Optional.empty()
                        : Optional.of("MOMO resultCode=" + resultCode);
    }

    private Map<String, String> withoutSignature(Map<String, String> parameters) {
        Map<String, String> sanitized = new LinkedHashMap<>(parameters);
        sanitized.remove("signature");
        return Map.copyOf(sanitized);
    }

    private void validateRequest(PaymentGatewayRequest request) {
        if (request == null || request.amount() == null || request.txnRef() == null || request.txnRef().isBlank()) {
            throw new IllegalArgumentException("MoMo payment request is incomplete");
        }
        if (request.amount().compareTo(BigDecimal.valueOf(MINIMUM_AMOUNT)) < 0) {
            throw new IllegalArgumentException("MoMo payment amount must be at least 1000 VND");
        }
        try {
            request.amount().longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("MoMo payment amount must be a whole VND amount", exception);
        }
    }

    private String createSignature(String partnerCode, String requestId, long amount, String orderId, String orderInfo) {
        String data = "accessKey=" + accessKey
                + "&amount=" + amount
                + "&extraData="
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + REQUEST_TYPE;
        return hmacSha256(data);
    }

    private String callbackSignatureData(Map<String, String> params) {
        return "accessKey=" + accessKey
                + "&amount=" + value(params, "amount")
                + "&extraData=" + value(params, "extraData")
                + "&message=" + value(params, "message")
                + "&orderId=" + value(params, "orderId")
                + "&orderInfo=" + value(params, "orderInfo")
                + "&orderType=" + value(params, "orderType")
                + "&partnerCode=" + value(params, "partnerCode")
                + "&payType=" + value(params, "payType")
                + "&requestId=" + value(params, "requestId")
                + "&responseTime=" + value(params, "responseTime")
                + "&resultCode=" + value(params, "resultCode")
                + "&transId=" + value(params, "transId");
    }

    private String value(Map<String, String> params, String key) {
        return params.getOrDefault(key, "");
    }

    private boolean isValidSignature(String suppliedSignature, String data) {
        if (suppliedSignature == null || suppliedSignature.isBlank()) {
            return false;
        }
        try {
            return MessageDigest.isEqual(hexToBytes(hmacSha256(data)), hexToBytes(suppliedSignature));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return bytesToHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Failed to generate MoMo signature", exception);
        }
    }

    private byte[] hexToBytes(String value) {
        if (value.length() % 2 != 0) {
            throw new IllegalArgumentException("Hex value must have an even number of characters");
        }
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Hex value contains non-hex characters");
            }
            bytes[i / 2] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
