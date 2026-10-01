package com.ticketbox.api.module.payment.gateways;

import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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
public class MomoPaymentStatusQueryGateway implements PaymentStatusQueryGateway {
    private final RestClient restClient;
    private final String queryUrl;
    private final String partnerCode;
    private final String accessKey;
    private final String secretKey;
    private final CircuitBreaker circuitBreaker;

    @Autowired
    public MomoPaymentStatusQueryGateway(
            @Value("${app.payment.momo.query-url:https://test-payment.momo.vn/v2/gateway/api/query}") String queryUrl,
            @Value("${app.payment.momo.partner-code}") String partnerCode,
            @Value("${app.payment.momo.access-key}") String accessKey,
            @Value("${app.payment.momo.secret-key}") String secretKey) {
        this(client(), queryUrl, partnerCode, accessKey, secretKey, breaker());
    }

    MomoPaymentStatusQueryGateway(RestClient restClient, String queryUrl, String partnerCode,
            String accessKey, String secretKey, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.queryUrl = queryUrl;
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
    public GatewayQueryResult query(Payment payment) {
        if (payment == null || payment.getId() == null || payment.getProvider() != PaymentProvider.MOMO) {
            throw new IllegalArgumentException("MoMo payment is required");
        }

        String orderId = payment.getId().toString();
        String requestId = UUID.randomUUID().toString();
        String signature = hmacSha256("accessKey=" + accessKey + "&orderId=" + orderId
                + "&partnerCode=" + partnerCode + "&requestId=" + requestId);
        Map<String, String> body = Map.of(
                "partnerCode", partnerCode,
                "requestId", requestId,
                "orderId", orderId,
                "lang", "vi",
                "signature", signature);

        MomoQueryResponse response = circuitBreaker.executeSupplier(() -> restClient.post()
                .uri(queryUrl)
                .body(body)
                .retrieve()
                .body(MomoQueryResponse.class));

        if (response == null || !partnerCode.equals(response.partnerCode())
                || !requestId.equals(response.requestId()) || !orderId.equals(response.orderId())
                || response.amount() == null || response.amount() <= 0 || response.resultCode() == null) {
            throw new IllegalStateException("MoMo query response is incomplete or mismatched");
        }

        PaymentStatus status = statusFor(response.resultCode());
        if (status == PaymentStatus.SUCCEEDED && response.transId() == null) {
            throw new IllegalStateException("Successful MoMo query has no transaction ID");
        }
        return new GatewayQueryResult(orderId, BigDecimal.valueOf(response.amount()),
                response.transId() == null ? null : response.transId().toString(),
                status, response.resultCode().toString());
    }

    private PaymentStatus statusFor(int code) {
        if (code == 0) {
            return PaymentStatus.SUCCEEDED;
        }
        if (code == 1003 || code == 1006 || code == 1017) {
            return PaymentStatus.CANCELLED;
        }
        if (code == 10 || code == 11 || code == 12 || code == 13
                || code == 20 || code == 21 || code == 22
                || code == 40 || code == 41 || code == 42 || code == 43
                || code == 45 || code == 47 || code == 1000
                || code == 7000 || code == 7002 || code == 9000) {
            return PaymentStatus.PENDING;
        }
        if (code == 98 || code == 99 || code == 1001 || code == 1002
                || code == 1004 || code == 1005
                || code == 1007 || code == 1026
                || code == 2019 || code == 4001 || code == 4002 || code == 4100) {
            return PaymentStatus.FAILED;
        }
        throw new IllegalStateException("MoMo query returned an inconclusive result code: " + code);
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Cannot sign MoMo query", exception);
        }
    }

    private static RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(35));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static CircuitBreaker breaker() {
        return CircuitBreaker.of("momo-query", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .recordException(exception -> exception instanceof ResourceAccessException
                        || exception instanceof HttpServerErrorException)
                .build());
    }

    private record MomoQueryResponse(String partnerCode, String requestId, String orderId,
            Long amount, Long transId, Integer resultCode) {
    }
}
