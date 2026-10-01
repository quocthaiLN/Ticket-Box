package com.ticketbox.api.module.payment.gateways;

import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class VnpayPaymentStatusQueryGateway implements PaymentStatusQueryGateway {
    private static final String VERSION = "2.1.0";
    private static final String COMMAND = "querydr";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestClient restClient;
    private final String queryUrl;
    private final String tmnCode;
    private final String hashSecret;
    private final String serverIp;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    public VnpayPaymentStatusQueryGateway(
            @Value("${app.payment.vnpay.query-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String queryUrl,
            @Value("${app.payment.vnpay.vnpay-tmn-code}") String tmnCode,
            @Value("${app.payment.vnpay.vnpay-hash-secret}") String hashSecret,
            @Value("${app.payment.vnpay.query-ip-address:127.0.0.1}") String serverIp) {
        this(client(), queryUrl, tmnCode, hashSecret, serverIp, breaker());
    }

    VnpayPaymentStatusQueryGateway(RestClient restClient, String queryUrl, String tmnCode,
            String hashSecret, String serverIp, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.queryUrl = queryUrl;
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.serverIp = serverIp;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.VNPAY;
    }

    @Override
    public GatewayQueryResult query(Payment payment) {
        if (payment == null || payment.getId() == null || payment.getProvider() != PaymentProvider.VNPAY
                || payment.getCheckoutUrl() == null) {
            throw new IllegalArgumentException("VNPay payment with checkout URL is required");
        }

        Map<String, String> checkout = checkoutParameters(payment.getCheckoutUrl());
        String txnRef = payment.getId().toString().replace("-", "");
        String transactionDate = required(checkout, "vnp_CreateDate");
        String orderInfo = required(checkout, "vnp_OrderInfo");
        if (!txnRef.equals(checkout.get("vnp_TxnRef"))) {
            throw new IllegalStateException("VNPay checkout URL has a different transaction reference");
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");
        String createDate = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DATE_FORMAT);
        Map<String, String> request = new HashMap<>();
        request.put("vnp_RequestId", requestId);
        request.put("vnp_Version", VERSION);
        request.put("vnp_Command", COMMAND);
        request.put("vnp_TmnCode", tmnCode);
        request.put("vnp_TxnRef", txnRef);
        request.put("vnp_TransactionDate", transactionDate);
        request.put("vnp_CreateDate", createDate);
        request.put("vnp_IpAddr", serverIp);
        request.put("vnp_OrderInfo", orderInfo);
        request.put("vnp_SecureHash", hmacSha512(String.join("|", requestId, VERSION, COMMAND,
                tmnCode, txnRef, transactionDate, createDate, serverIp, orderInfo)));

        @SuppressWarnings("unchecked")
        Map<String, Object> rawResponse = circuitBreaker.executeSupplier(() -> restClient.post()
                .uri(queryUrl)
                .body(request)
                .retrieve()
                .body(Map.class));
        if (rawResponse == null) {
            throw new IllegalStateException("VNPay query returned no response");
        }
        Map<String, String> response = new HashMap<>();
        rawResponse.forEach((key, value) -> response.put(key, value == null ? "" : value.toString()));
        if (!validSignature(response)) {
            throw new IllegalStateException("VNPay query response has invalid checksum");
        }
        if (!tmnCode.equals(response.get("vnp_TmnCode")) || !txnRef.equals(response.get("vnp_TxnRef"))
                || !COMMAND.equals(response.get("vnp_Command"))) {
            throw new IllegalStateException("VNPay query response does not match payment");
        }
        if (!"00".equals(response.get("vnp_ResponseCode"))) {
            throw new IllegalStateException("VNPay query did not return transaction status: "
                    + response.get("vnp_ResponseCode"));
        }
        if (!"01".equals(response.get("vnp_TransactionType"))) {
            throw new IllegalStateException("VNPay query returned a non-payment transaction");
        }

        PaymentStatus status = statusFor(response.get("vnp_TransactionStatus"));
        String transactionId = response.get("vnp_TransactionNo");
        if (status == PaymentStatus.SUCCEEDED && (transactionId == null || transactionId.isBlank())) {
            throw new IllegalStateException("Successful VNPay query has no transaction ID");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(required(response, "vnp_Amount")).movePointLeft(2);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("VNPay query has invalid amount", exception);
        }
        return new GatewayQueryResult(txnRef, amount, transactionId, status,
                response.get("vnp_TransactionStatus"));
    }

    private PaymentStatus statusFor(String status) {
        return switch (status == null ? "" : status) {
            case "00" -> PaymentStatus.SUCCEEDED;
            case "01" -> PaymentStatus.PENDING;
            case "02" -> PaymentStatus.FAILED;
            default -> throw new IllegalStateException("VNPay query returned an inconclusive status: " + status);
        };
    }

    private boolean validSignature(Map<String, String> response) {
        String supplied = response.get("vnp_SecureHash");
        if (supplied == null || supplied.isBlank()) {
            return false;
        }
        String data = String.join("|", value(response, "vnp_ResponseId"), value(response, "vnp_Command"),
                value(response, "vnp_ResponseCode"), value(response, "vnp_Message"),
                value(response, "vnp_TmnCode"), value(response, "vnp_TxnRef"),
                value(response, "vnp_Amount"), value(response, "vnp_BankCode"),
                value(response, "vnp_PayDate"), value(response, "vnp_TransactionNo"),
                value(response, "vnp_TransactionType"), value(response, "vnp_TransactionStatus"),
                value(response, "vnp_OrderInfo"), value(response, "vnp_PromotionCode"),
                value(response, "vnp_PromotionAmount"));
        try {
            return MessageDigest.isEqual(HexFormat.of().parseHex(hmacSha512(data)),
                    HexFormat.of().parseHex(supplied));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String hmacSha512(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Cannot sign VNPay query", exception);
        }
    }

    private Map<String, String> checkoutParameters(String checkoutUrl) {
        String rawQuery = URI.create(checkoutUrl).getRawQuery();
        if (rawQuery == null) {
            throw new IllegalStateException("VNPay checkout URL has no query parameters");
        }
        Map<String, String> params = new HashMap<>();
        Arrays.stream(rawQuery.split("&")).forEach(pair -> {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                params.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        });
        return params;
    }

    private String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing VNPay field: " + name);
        }
        return value;
    }

    private String value(Map<String, String> values, String name) {
        return values.getOrDefault(name, "");
    }

    private static RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(35));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static CircuitBreaker breaker() {
        return CircuitBreaker.of("vnpay-query", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordException(exception -> exception instanceof ResourceAccessException
                        || exception instanceof HttpServerErrorException)
                .build());
    }
}
