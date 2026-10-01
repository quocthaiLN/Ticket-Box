package com.ticketbox.api.module.payment.gateways;

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
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
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
    private final CircuitBreaker circuitBreaker;

    @Autowired
    public MomoGateway(
            @Value("${app.payment.momo.momo-url}") String createUrl,
            @Value("${app.payment.momo.partner-code}") String partnerCode,
            @Value("${app.payment.momo.access-key}") String accessKey,
            @Value("${app.payment.momo.secret-key}") String secretKey,
            @Value("${app.payment.momo.redirect-url}") String redirectUrl,
            @Value("${app.payment.momo.ipn-url}") String ipnUrl) {
        this(client(), createUrl, partnerCode, accessKey, secretKey, redirectUrl, ipnUrl);
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
        this.circuitBreaker = breaker();
    }

    @Override
    public String createPaymentUrl(PaymentGatewayRequest request) {
        try {
            validateRequest(request);
        } catch (IllegalArgumentException exception) {
            throw new CreateRejectedException("MoMo payment request is invalid", exception);
        }

        String orderId = request.txnRef();
        String requestId = orderId;
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
            response = circuitBreaker.executeSupplier(() -> restClient.post()
                    .uri(createUrl)
                    .body(body)
                    .retrieve()
                    .body(MomoCreatePaymentResponse.class));
        } catch (HttpClientErrorException exception) {
            throw new CreateOutcomeUnknownException("MoMo create response needs reconciliation", exception);
        } catch (CallNotPermittedException | RestClientException exception) {
            throw new CreateOutcomeUnknownException("MoMo create result is unknown", exception);
        }

        if (response == null || response.resultCode() == null) {
            throw new CreateOutcomeUnknownException("MoMo create response is incomplete");
        }
        if (response.resultCode() != 0) {
            if (!isFinalCreateRejection(response.resultCode())) {
                throw new CreateOutcomeUnknownException("MoMo create is in progress");
            }
            throw new CreateRejectedException("MoMo create resultCode=" + response.resultCode());
        }
        if (response.payUrl() == null || response.payUrl().isBlank()) {
            throw new CreateOutcomeUnknownException("MoMo returned success without a payment URL");
        }
        return response.payUrl();
    }

    private boolean isFinalCreateRejection(int resultCode) {
        return resultCode == 98 || resultCode == 99 || resultCode == 1001
                || resultCode == 1002 || resultCode == 1003
                || resultCode == 1004 || resultCode == 1005 || resultCode == 1006
                || resultCode == 1007 || resultCode == 1017 || resultCode == 1026
                || resultCode == 2019 || resultCode == 4001 || resultCode == 4002
                || resultCode == 4100;
    }

    public static final class CreateOutcomeUnknownException extends RuntimeException {
        public CreateOutcomeUnknownException(String message) {
            super(message);
        }

        public CreateOutcomeUnknownException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class CreateRejectedException extends RuntimeException {
        public CreateRejectedException(String message) {
            super(message);
        }

        public CreateRejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(35));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static CircuitBreaker breaker() {
        return CircuitBreaker.of("momo-create", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordException(exception -> exception instanceof ResourceAccessException
                        || exception instanceof HttpServerErrorException)
                .build());
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
        try {
            int code = Integer.parseInt(resultCode);
            if (code == 0) {
                return Optional.of(PaymentStatus.SUCCEEDED);
            }
            if (code == 1003 || code == 1006 || code == 1017) {
                return Optional.of(PaymentStatus.CANCELLED);
            }
            return Optional.of(isFinalCreateRejection(code) ? PaymentStatus.FAILED : PaymentStatus.PENDING);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> failureReason(String resultCode) {
        PaymentStatus status = targetStatus(resultCode).orElse(PaymentStatus.PENDING);
        return status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED
                ? Optional.of("MOMO resultCode=" + resultCode)
                : Optional.empty();
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
