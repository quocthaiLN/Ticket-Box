package com.ticketbox.api.module.payment.gateways;

import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayRequest;
import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayResult;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class VnpayRefundGateway implements PaymentRefundGateway {
    private static final Logger log = LoggerFactory.getLogger(VnpayRefundGateway.class);
    private static final String VERSION = "2.1.0";
    private static final String COMMAND = "refund";
    private static final String TRANSACTION_TYPE_FULL = "02";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestClient restClient;
    private final String refundUrl;
    private final String tmnCode;
    private final String hashSecret;
    private final String serverIp;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    public VnpayRefundGateway(
            @Value("${app.payment.vnpay.refund-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String refundUrl,
            @Value("${app.payment.vnpay.vnpay-tmn-code}") String tmnCode,
            @Value("${app.payment.vnpay.vnpay-hash-secret}") String hashSecret,
            @Value("${app.payment.vnpay.query-ip-address:127.0.0.1}") String serverIp) {
        this(client(), refundUrl, tmnCode, hashSecret, serverIp, breaker());
    }

    VnpayRefundGateway(RestClient restClient, String refundUrl, String tmnCode,
            String hashSecret, String serverIp, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.refundUrl = refundUrl;
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
    public RefundGatewayResult refund(RefundGatewayRequest request) {
        if (request == null || request.refundId() == null) {
            throw new IllegalArgumentException("Refund ID is required");
        }
        if (request.merchantTxnRef() == null || request.merchantTxnRef().isBlank()) {
            throw new IllegalArgumentException("Merchant transaction reference is required for VNPay refund");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }

        String requestId = request.refundId().toString().replace("-", "");
        String txnRef = request.merchantTxnRef();
        String amountStr = request.amount().multiply(BigDecimal.valueOf(100)).toBigInteger().toString();
        String transactionNo = (request.providerTxnId() != null && !request.providerTxnId().isBlank())
                ? request.providerTxnId()
                : "0";
        String transactionDate = (request.transDate() != null && !request.transDate().isBlank())
                ? request.transDate()
                : LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DATE_FORMAT);
        String createBy = "TicketBox";
        String createDate = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).format(DATE_FORMAT);
        String orderInfo = (request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : "Refund order " + txnRef;

        String dataToSign = String.join("|",
                requestId,
                VERSION,
                COMMAND,
                tmnCode,
                TRANSACTION_TYPE_FULL,
                txnRef,
                amountStr,
                transactionNo,
                transactionDate,
                createBy,
                createDate,
                serverIp,
                orderInfo);
        String secureHash = hmacSha512(dataToSign);

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("vnp_RequestId", requestId);
        requestBody.put("vnp_Version", VERSION);
        requestBody.put("vnp_Command", COMMAND);
        requestBody.put("vnp_TmnCode", tmnCode);
        requestBody.put("vnp_TransactionType", TRANSACTION_TYPE_FULL);
        requestBody.put("vnp_TxnRef", txnRef);
        requestBody.put("vnp_Amount", amountStr);
        requestBody.put("vnp_OrderInfo", orderInfo);
        requestBody.put("vnp_TransactionNo", transactionNo);
        requestBody.put("vnp_TransactionDate", transactionDate);
        requestBody.put("vnp_CreateBy", createBy);
        requestBody.put("vnp_CreateDate", createDate);
        requestBody.put("vnp_IpAddr", serverIp);
        requestBody.put("vnp_SecureHash", secureHash);

        Map<String, Object> rawResponse;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = circuitBreaker.executeSupplier(() -> restClient.post()
                    .uri(refundUrl)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class));
            rawResponse = result;
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("VNPay refund call failed with network/server error: {}", exception.getMessage());
            return RefundGatewayResult.builder()
                    .status(RefundStatus.PROCESSING)
                    .responseCode("GATEWAY_TIMEOUT")
                    .message(exception.getMessage())
                    .build();
        } catch (CallNotPermittedException exception) {
            log.warn("VNPay refund circuit breaker is OPEN: {}", exception.getMessage());
            return RefundGatewayResult.builder()
                    .status(RefundStatus.PROCESSING)
                    .responseCode("CIRCUIT_BREAKER_OPEN")
                    .message(exception.getMessage())
                    .build();
        }

        if (rawResponse == null) {
            throw new IllegalStateException("VNPay refund returned no response");
        }

        Map<String, String> response = new HashMap<>();
        rawResponse.forEach((key, value) -> response.put(key, value == null ? "" : value.toString()));

        if (!validSignature(response)) {
            throw new IllegalStateException("VNPay refund response has invalid checksum");
        }

        String responseCode = response.getOrDefault("vnp_ResponseCode", "");
        String transactionStatus = response.getOrDefault("vnp_TransactionStatus", "");
        String message = response.getOrDefault("vnp_Message", "");
        String transactionNoResponse = response.getOrDefault("vnp_TransactionNo", "");
        String responseId = response.getOrDefault("vnp_ResponseId", "");

        RefundStatus status = mapResponseCode(responseCode, transactionStatus);
        String providerRefundId = !transactionNoResponse.isBlank()
                ? transactionNoResponse
                : (!responseId.isBlank() ? responseId : null);

        return RefundGatewayResult.builder()
                .providerRefundId(providerRefundId)
                .status(status)
                .responseCode(responseCode)
                .message(message)
                .build();
    }

    private RefundStatus mapResponseCode(String responseCode, String transactionStatus) {
        if ("00".equals(responseCode)) {
            if ("00".equals(transactionStatus) || transactionStatus.isBlank()) {
                return RefundStatus.SUCCESS;
            }
            if ("01".equals(transactionStatus)) {
                return RefundStatus.PROCESSING;
            }
            return RefundStatus.FAILED;
        }
        if ("91".equals(responseCode) || "94".equals(responseCode)) {
            return RefundStatus.PROCESSING;
        }
        return RefundStatus.FAILED;
    }

    private boolean validSignature(Map<String, String> response) {
        String supplied = response.get("vnp_SecureHash");
        if (supplied == null || supplied.isBlank()) {
            return false;
        }
        String data = String.join("|",
                value(response, "vnp_ResponseId"),
                value(response, "vnp_Command"),
                value(response, "vnp_ResponseCode"),
                value(response, "vnp_Message"),
                value(response, "vnp_TmnCode"),
                value(response, "vnp_TxnRef"),
                value(response, "vnp_Amount"),
                value(response, "vnp_BankCode"),
                value(response, "vnp_PayDate"),
                value(response, "vnp_TransactionNo"),
                value(response, "vnp_TransactionType"),
                value(response, "vnp_TransactionStatus"),
                value(response, "vnp_OrderInfo"));
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
            throw new IllegalStateException("Cannot sign VNPay refund request", exception);
        }
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
        return CircuitBreaker.of("vnpay-refund", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordException(exception -> exception instanceof ResourceAccessException
                        || exception instanceof HttpServerErrorException)
                .build());
    }
}
