package com.ticketbox.api.module.payment.gateways;

import com.ticketbox.api.module.payment.domain.dtos.PaymentGatewayRequest;
import com.ticketbox.api.module.payment.domain.dtos.PaymentVerificationResult;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VnpayGateway implements PaymentGatewayStrategy {
    private static final String VERSION = "2.1.0";
    private static final String COMMAND = "pay";
    private static final String DEFAULT_LOCALE = "vn";
    private static final String DEFAULT_CURRENCY = "VND";
    private static final String DEFAULT_ORDER_TYPE = "other";
    private static final String DEFAULT_IP = "127.0.0.1";
    private static final long EXPIRY_MINUTES = 15;

    private final String vnpPayUrl;
    private final String vnpTmnCode;
    private final String vnpHashSecret;
    private final String vnpReturnUrl;

    public VnpayGateway(
            @Value("${app.payment.vnpay.vnpay-url}") String vnpPayUrl,
            @Value("${app.payment.vnpay.vnpay-tmn-code}") String vnpTmnCode,
            @Value("${app.payment.vnpay.vnpay-hash-secret}") String vnpHashSecret,
            @Value("${app.payment.vnpay.vnpay-return-url}") String vnpReturnUrl) {
        this.vnpPayUrl = vnpPayUrl;
        this.vnpTmnCode = vnpTmnCode;
        this.vnpHashSecret = vnpHashSecret;
        this.vnpReturnUrl = vnpReturnUrl;
    }

    @Override
    public String createPaymentUrl(PaymentGatewayRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Payment request cannot be null");
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than 0");
        }

        String txnRef = (request.txnRef() != null && !request.txnRef().isBlank())
                ? request.txnRef()
                : UUID.randomUUID().toString().replace("-", "");

        String orderInfo = (request.orderInfo() != null && !request.orderInfo().isBlank())
                ? request.orderInfo()
                : "Payment for order " + (request.orderId() != null ? request.orderId() : "N/A");

        String currency = (request.currency() != null && !request.currency().isBlank())
                ? request.currency()
                : DEFAULT_CURRENCY;

        String returnUrl = (request.returnUrl() != null && !request.returnUrl().isBlank())
                ? request.returnUrl()
                : vnpReturnUrl;

        String locale = (request.locale() != null && !request.locale().isBlank())
                ? request.locale()
                : DEFAULT_LOCALE;

        String ipAddress = (request.ipAddress() != null && !request.ipAddress().isBlank())
                ? request.ipAddress()
                : DEFAULT_IP;

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String createDate = formatDate(now);
        String expireDate = formatDate(now.plusMinutes(EXPIRY_MINUTES));

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", VERSION);
        params.put("vnp_Command", COMMAND);
        params.put("vnp_TmnCode", vnpTmnCode);
        params.put("vnp_Amount", String.valueOf(request.amount().multiply(BigDecimal.valueOf(100)).longValue()));
        params.put("vnp_CurrCode", currency);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", orderInfo);
        params.put("vnp_OrderType", request.orderType() != null ? request.orderType() : DEFAULT_ORDER_TYPE);
        params.put("vnp_Locale", locale);
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", ipAddress);
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_ExpireDate", expireDate);

        if (request.bankCode() != null && !request.bankCode().isBlank()) {
            params.put("vnp_BankCode", request.bankCode());
        }

        if (request.extraData() != null && !request.extraData().isBlank()) {
            params.put("vnp_ExtraData", request.extraData());
        }

        String hashData = buildHashData(params);
        String secureHash = generateSecureHash(hashData, vnpHashSecret);
        String queryString = buildQueryString(params);

        return vnpPayUrl + "?" + queryString + "&vnp_SecureHash=" + secureHash;
    }

    @Override
    public PaymentVerificationResult verifyCallback(Map<String, String> params) {
        Map<String, String> callbackParams = params == null ? Map.of() : params;
        String providedSecureHash = callbackParams.get("vnp_SecureHash");

        Map<String, String> signedParams = new TreeMap<>();
        callbackParams.forEach((key, value) -> {
            if (key != null
                    && key.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)
                    && value != null
                    && !value.isBlank()) {
                signedParams.put(key, value);
            }
        });

        boolean signatureValid = isValidSecureHash(providedSecureHash, buildHashData(signedParams));
        return new PaymentVerificationResult(
                signatureValid,
                callbackParams.get("vnp_TxnRef"),
                callbackParams.get("vnp_TransactionNo"),
                callbackParams.get("vnp_ResponseCode"),
                callbackParams.get("vnp_TransactionStatus"),
                callbackParams.get("vnp_Amount"));
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.VNPAY;
    }

    private String buildHashData(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String buildQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String generateSecureHash(String data, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to generate VNPAY secure hash", e);
        }
    }

    private boolean isValidSecureHash(String providedSecureHash, String hashData) {
        if (providedSecureHash == null || providedSecureHash.isBlank()) {
            return false;
        }

        try {
            byte[] expectedHash = hexToBytes(generateSecureHash(hashData, vnpHashSecret));
            byte[] receivedHash = hexToBytes(providedSecureHash);
            return MessageDigest.isEqual(expectedHash, receivedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] hexToBytes(String value) {
        int length = value.length();
        if (length % 2 != 0) {
            throw new IllegalArgumentException("Hex value must have an even number of characters");
        }

        byte[] bytes = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high == -1 || low == -1) {
                throw new IllegalArgumentException("Hex value contains a non-hexadecimal character");
            }
            bytes[i / 2] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
}
