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
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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
public class MomoRefundGateway implements PaymentRefundGateway {
    private static final Logger log = LoggerFactory.getLogger(MomoRefundGateway.class);

    private final RestClient restClient;
    private final String refundUrl;
    private final String partnerCode;
    private final String accessKey;
    private final String secretKey;
    private final CircuitBreaker circuitBreaker;

    // Dùng cho Spring IoC container
    @Autowired
    public MomoRefundGateway(
            @Value("${app.payment.momo.refund-url:https://test-payment.momo.vn/v2/gateway/api/refund}") String refundUrl,
            @Value("${app.payment.momo.partner-code}") String partnerCode,
            @Value("${app.payment.momo.access-key}") String accessKey,
            @Value("${app.payment.momo.secret-key}") String secretKey) {
        this(client(), refundUrl, partnerCode, accessKey, secretKey, breaker());
    }

    // Package-private dùng cho Unit Test
    MomoRefundGateway(RestClient restClient, String refundUrl, String partnerCode,
            String accessKey, String secretKey, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.refundUrl = refundUrl;
        this.partnerCode = partnerCode;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.MOMO;
    }

    @Override
    public RefundGatewayResult refund(RefundGatewayRequest request) {
        // Input Validation
        if (request == null || request.refundId() == null) {
            throw new IllegalArgumentException("Refund ID is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }
        if (request.providerTxnId() == null || request.providerTxnId().isBlank()) {
            throw new IllegalArgumentException("Original provider transaction ID is required for MoMo refund");
        }

        long transId;
        try {
            transId = Long.parseLong(request.providerTxnId());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid MoMo transaction ID format: " + request.providerTxnId(), exception);
        }

        // Chuẩn bị các tham số cho giao dịch hoàn tiền
        String orderId = (request.merchantTxnRef() != null && !request.merchantTxnRef().isBlank())
                ? request.merchantTxnRef()
                : request.refundId().toString();
        String requestId = request.refundId().toString();
        long amount = request.amount().longValue();
        String description = (request.reason() != null && !request.reason().isBlank())
                ? request.reason()
                : "Refund order " + orderId;

        // Tạo Signature
        String signature = hmacSha256("accessKey=" + accessKey
                + "&amount=" + amount
                + "&description=" + description
                + "&orderId=" + orderId
                + "&partnerCode=" + partnerCode
                + "&requestId=" + requestId
                + "&transId=" + transId);

        // Tạo Payload = thông tin refudn + Signature
        Map<String, Object> body = Map.of(
                "partnerCode", partnerCode,
                "orderId", orderId,
                "requestId", requestId,
                "amount", amount,
                "transId", transId,
                "lang", "vi",
                "description", description,
                "signature", signature);

        // Gửi HTTP POST request được bảo vệ bởi CircuitBreaker
        MomoRefundResponse response;
        try {
            response = circuitBreaker.executeSupplier(() -> restClient.post()
                    .uri(refundUrl)
                    .body(body)
                    .retrieve()
                    .body(MomoRefundResponse.class));
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            // Lỗi mạng
            log.warn("MoMo refund call failed with network/server error: {}", exception.getMessage());
            return RefundGatewayResult.builder()
                    .status(RefundStatus.PROCESSING)
                    .responseCode("GATEWAY_TIMEOUT")
                    .message(exception.getMessage())
                    .build();
        } catch (CallNotPermittedException exception) {
            // Lỗi Circuit breaker open
            log.warn("MoMo refund circuit breaker is OPEN: {}", exception.getMessage());
            return RefundGatewayResult.builder()
                    .status(RefundStatus.PROCESSING)
                    .responseCode("CIRCUIT_BREAKER_OPEN")
                    .message(exception.getMessage())
                    .build();
        }

        // Đã gửi và nhận phản hồi từ Gateway
        if (response == null || response.resultCode() == null) {
            throw new IllegalStateException("MoMo refund response is incomplete");
        }

        // Ánh xạ và return
        RefundStatus status = mapResultCode(response.resultCode());
        String providerRefundId = response.transId() != null
                ? response.transId().toString()
                : (status == RefundStatus.SUCCESS ? String.valueOf(transId) : null);

        return RefundGatewayResult.builder()
                .providerRefundId(providerRefundId)
                .status(status)
                .responseCode(response.resultCode().toString())
                .message(response.message())
                .build();
    }

    private RefundStatus mapResultCode(int code) {
        if (code == 0) {
            return RefundStatus.SUCCESS;
        }
        if (code == 1000) {
            return RefundStatus.PROCESSING;
        }
        return RefundStatus.FAILED;
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Cannot sign MoMo refund request", exception);
        }
    }

    private static RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(35));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static CircuitBreaker breaker() {
        return CircuitBreaker.of("momo-refund", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordException(exception -> exception instanceof ResourceAccessException
                        || exception instanceof HttpServerErrorException)
                .build());
    }

    private record MomoRefundResponse(
            String partnerCode,
            String orderId,
            String requestId,
            Long amount,
            Long transId,
            Integer resultCode,
            String message) {
    }
}
