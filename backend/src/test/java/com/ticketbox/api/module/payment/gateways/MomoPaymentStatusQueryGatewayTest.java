package com.ticketbox.api.module.payment.gateways;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MomoPaymentStatusQueryGatewayTest {
    private static final String QUERY_URL = "https://test-payment.momo.vn/v2/gateway/api/query";
    private static final String PARTNER = "MOMO_TEST";
    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key";
    private static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void signsQueryAndAcceptsMatchingSuccessfulResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MomoPaymentStatusQueryGateway gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("test-momo-query"));

        server.expect(once(), requestTo(QUERY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    JsonNode body = objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    String requestId = body.get("requestId").asText();
                    String signedData = "accessKey=" + ACCESS_KEY + "&orderId=" + PAYMENT_ID
                            + "&partnerCode=" + PARTNER + "&requestId=" + requestId;
                    assertThat(body.get("signature").asText()).isEqualTo(hmac(signedData));
                    return withSuccess("""
                            {"partnerCode":"%s","requestId":"%s","orderId":"%s",
                             "amount":1000,"transId":4088878653,"resultCode":0}
                            """.formatted(PARTNER, requestId, PAYMENT_ID), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        GatewayQueryResult result = gateway.query(payment());

        assertThat(result.merchantReference()).isEqualTo(PAYMENT_ID.toString());
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(result.providerTransactionId()).isEqualTo("4088878653");
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        server.verify();
    }

    @Test
    void rejectsResponseForAnotherRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MomoPaymentStatusQueryGateway gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("test-momo-mismatch"));
        server.expect(once(), requestTo(QUERY_URL))
                .andRespond(withSuccess("""
                        {"partnerCode":"MOMO_TEST","requestId":"wrong","orderId":"%s",
                         "amount":1000,"transId":4088878653,"resultCode":0}
                        """.formatted(PAYMENT_ID), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.query(payment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mismatched");
        server.verify();
    }

    @Test
    void serverErrorOpensBreakerWithoutChangingPayment() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CircuitBreaker breaker = CircuitBreaker.of("test-momo-server-error", CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(1)
                .slidingWindowSize(1)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordException(exception -> exception instanceof ResourceAccessException
                        || exception instanceof HttpServerErrorException)
                .build());
        MomoPaymentStatusQueryGateway gateway = gateway(builder.build(), breaker);
        server.expect(once(), requestTo(QUERY_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.query(payment())).isInstanceOf(HttpServerErrorException.class);
        assertThatThrownBy(() -> gateway.query(payment())).isInstanceOf(CallNotPermittedException.class);
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"1003,CANCELLED", "1006,CANCELLED", "1017,CANCELLED",
            "1001,FAILED", "0,SUCCEEDED", "1000,PENDING"})
    void mapsQueryOutcomes(int code, PaymentStatus expected) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("mapping"));
        server.expect(requestTo(QUERY_URL)).andRespond(request -> {
            JsonNode body = objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
            return withSuccess("""
                    {"partnerCode":"MOMO_TEST","requestId":"%s","orderId":"%s",
                     "amount":1000,"transId":4088878653,"resultCode":%d}
                    """.formatted(body.get("requestId").asText(), PAYMENT_ID, code), MediaType.APPLICATION_JSON)
                    .createResponse(request);
        });
        assertThat(gateway.query(payment()).status()).isEqualTo(expected);
        server.verify();
    }

    private MomoPaymentStatusQueryGateway gateway(RestClient restClient, CircuitBreaker breaker) {
        return new MomoPaymentStatusQueryGateway(restClient, QUERY_URL, PARTNER, ACCESS_KEY, SECRET_KEY, breaker);
    }

    private Payment payment() {
        return Payment.builder().id(PAYMENT_ID).provider(PaymentProvider.MOMO).amount(new BigDecimal("1000")).build();
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
