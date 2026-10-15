package com.ticketbox.api.module.payment.gateways;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayRequest;
import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayResult;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class MomoRefundGatewayTest {
    private static final String REFUND_URL = "https://test-payment.momo.vn/v2/gateway/api/refund";
    private static final String PARTNER = "MOMO_TEST";
    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key";
    private static final UUID REFUND_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String ORDER_ID = "ORDER_123456";
    private static final String TRANS_ID = "9988776655";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void providerReturnsMomo() {
        MomoRefundGateway gateway = gateway(RestClient.builder().build(), CircuitBreaker.ofDefaults("test"));
        assertThat(gateway.getProvider()).isEqualTo(PaymentProvider.MOMO);
    }

    @Test
    void signsRefundAndAcceptsSuccessfulResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MomoRefundGateway gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("test-momo-refund"));

        server.expect(once(), requestTo(REFUND_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    JsonNode body = objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.get("partnerCode").asText()).isEqualTo(PARTNER);
                    assertThat(body.get("orderId").asText()).isEqualTo(ORDER_ID);
                    assertThat(body.get("requestId").asText()).isEqualTo(REFUND_ID.toString());
                    assertThat(body.get("amount").asLong()).isEqualTo(500000L);
                    assertThat(body.get("transId").asLong()).isEqualTo(9988776655L);
                    assertThat(body.get("description").asText()).isEqualTo("Late refund after hold expiry");

                    String expectedData = "accessKey=" + ACCESS_KEY
                            + "&amount=500000"
                            + "&description=Late refund after hold expiry"
                            + "&orderId=" + ORDER_ID
                            + "&partnerCode=" + PARTNER
                            + "&requestId=" + REFUND_ID
                            + "&transId=" + TRANS_ID;
                    assertThat(body.get("signature").asText()).isEqualTo(hmac(expectedData));

                    return withSuccess("""
                            {
                              "partnerCode": "%s",
                              "orderId": "%s",
                              "requestId": "%s",
                              "amount": 500000,
                              "transId": 1122334455,
                              "resultCode": 0,
                              "message": "Thành công"
                            }
                            """.formatted(PARTNER, ORDER_ID, REFUND_ID), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        RefundGatewayRequest request = RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(ORDER_ID)
                .providerTxnId(TRANS_ID)
                .amount(new BigDecimal("500000"))
                .reason("Late refund after hold expiry")
                .build();

        RefundGatewayResult result = gateway.refund(request);

        assertThat(result.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(result.providerRefundId()).isEqualTo("1122334455");
        assertThat(result.responseCode()).isEqualTo("0");
        assertThat(result.message()).isEqualTo("Thành công");
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "1000, PROCESSING",
            "1004, FAILED",
            "99, FAILED"
    })
    void mapsRefundResultCodes(int code, RefundStatus expectedStatus) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MomoRefundGateway gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("test-momo-mapping"));

        server.expect(once(), requestTo(REFUND_URL))
                .andRespond(withSuccess("""
                        {
                          "partnerCode": "%s",
                          "orderId": "%s",
                          "requestId": "%s",
                          "amount": 500000,
                          "transId": 1122334455,
                          "resultCode": %d,
                          "message": "Test response"
                        }
                        """.formatted(PARTNER, ORDER_ID, REFUND_ID, code), MediaType.APPLICATION_JSON));

        RefundGatewayRequest request = RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(ORDER_ID)
                .providerTxnId(TRANS_ID)
                .amount(new BigDecimal("500000"))
                .build();

        RefundGatewayResult result = gateway.refund(request);

        assertThat(result.status()).isEqualTo(expectedStatus);
        assertThat(result.responseCode()).isEqualTo(String.valueOf(code));
        server.verify();
    }

    @Test
    void handlesNetworkTimeoutGracefullyWithProcessingStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MomoRefundGateway gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("test-momo-timeout"));

        server.expect(once(), requestTo(REFUND_URL))
                .andRespond(request -> {
                    throw new ResourceAccessException("Connection timed out");
                });

        RefundGatewayRequest request = RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(ORDER_ID)
                .providerTxnId(TRANS_ID)
                .amount(new BigDecimal("500000"))
                .build();

        RefundGatewayResult result = gateway.refund(request);

        assertThat(result.status()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(result.responseCode()).isEqualTo("GATEWAY_TIMEOUT");
        server.verify();
    }

    @Test
    void circuitBreakerOpensOnRepeatedFailuresAndReturnsProcessingStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CircuitBreaker breaker = CircuitBreaker.of("test-momo-cb", CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(1)
                .slidingWindowSize(1)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordException(exception -> exception instanceof ResourceAccessException)
                .build());

        MomoRefundGateway gateway = gateway(builder.build(), breaker);

        server.expect(once(), requestTo(REFUND_URL))
                .andRespond(request -> {
                    throw new ResourceAccessException("Connection timed out");
                });

        RefundGatewayRequest request = RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(ORDER_ID)
                .providerTxnId(TRANS_ID)
                .amount(new BigDecimal("500000"))
                .build();

        RefundGatewayResult firstResult = gateway.refund(request);
        assertThat(firstResult.status()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(firstResult.responseCode()).isEqualTo("GATEWAY_TIMEOUT");

        // Call again while breaker is OPEN
        RefundGatewayResult secondResult = gateway.refund(request);
        assertThat(secondResult.status()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(secondResult.responseCode()).isEqualTo("CIRCUIT_BREAKER_OPEN");
        server.verify();
    }

    @Test
    void validatesRequiredInputs() {
        MomoRefundGateway gateway = gateway(RestClient.builder().build(), CircuitBreaker.ofDefaults("test"));

        assertThatThrownBy(() -> gateway.refund(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund ID is required");

        assertThatThrownBy(() -> gateway.refund(RefundGatewayRequest.builder().build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund ID is required");

        assertThatThrownBy(() -> gateway.refund(RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund amount must be positive");

        assertThatThrownBy(() -> gateway.refund(RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .amount(BigDecimal.ZERO)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund amount must be positive");

        assertThatThrownBy(() -> gateway.refund(RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .amount(new BigDecimal("1000"))
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Original provider transaction ID is required");

        assertThatThrownBy(() -> gateway.refund(RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .amount(new BigDecimal("1000"))
                .providerTxnId("not-a-number")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid MoMo transaction ID format");
    }

    private MomoRefundGateway gateway(RestClient restClient, CircuitBreaker breaker) {
        return new MomoRefundGateway(restClient, REFUND_URL, PARTNER, ACCESS_KEY, SECRET_KEY, breaker);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
