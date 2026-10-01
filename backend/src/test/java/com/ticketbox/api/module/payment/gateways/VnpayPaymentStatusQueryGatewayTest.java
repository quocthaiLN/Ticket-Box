package com.ticketbox.api.module.payment.gateways;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class VnpayPaymentStatusQueryGatewayTest {
    private static final String QUERY_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private static final String TMN_CODE = "TESTCODE";
    private static final String SECRET = "test-vnpay-hash-secret";
    private static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String TXN_REF = "11111111111141118111111111111111";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void signsQueryAndVerifiesSuccessfulResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VnpayPaymentStatusQueryGateway gateway = gateway(builder.build());
        Map<String, String> response = signedResponse();

        server.expect(once(), requestTo(QUERY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    JsonNode body = objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    String signedData = String.join("|", body.get("vnp_RequestId").asText(), "2.1.0", "querydr",
                            TMN_CODE, TXN_REF, "20261001120000", body.get("vnp_CreateDate").asText(),
                            "127.0.0.1", "Payment order");
                    assertThat(body.get("vnp_SecureHash").asText()).isEqualTo(hmac(signedData));
                    return withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        GatewayQueryResult result = gateway.query(payment());

        assertThat(result.merchantReference()).isEqualTo(TXN_REF);
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("1234.50"));
        assertThat(result.providerTransactionId()).isEqualTo("1234567");
        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        server.verify();
    }

    @Test
    void rejectsTamperedSignedResponse() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VnpayPaymentStatusQueryGateway gateway = gateway(builder.build());
        Map<String, String> response = signedResponse();
        response.put("vnp_Amount", "999999");
        server.expect(once(), requestTo(QUERY_URL))
                .andRespond(withSuccess(objectMapper.writeValueAsString(response), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.query(payment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
        server.verify();
    }

    @Test
    void timeoutOpensBreakerWithoutTurningPaymentIntoFailure() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CircuitBreaker breaker = CircuitBreaker.of("test-vnpay-timeout", CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(1)
                .slidingWindowSize(1)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordException(exception -> exception instanceof ResourceAccessException)
                .build());
        VnpayPaymentStatusQueryGateway gateway = new VnpayPaymentStatusQueryGateway(
                builder.build(), QUERY_URL, TMN_CODE, SECRET, "127.0.0.1", breaker);
        Payment payment = payment();
        server.expect(once(), requestTo(QUERY_URL))
                .andRespond(request -> { throw new ResourceAccessException("Read timed out"); });

        assertThatThrownBy(() -> gateway.query(payment)).isInstanceOf(ResourceAccessException.class);
        assertThatThrownBy(() -> gateway.query(payment)).isInstanceOf(CallNotPermittedException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        server.verify();
    }

    private VnpayPaymentStatusQueryGateway gateway(RestClient restClient) {
        return new VnpayPaymentStatusQueryGateway(restClient, QUERY_URL, TMN_CODE, SECRET,
                "127.0.0.1", CircuitBreaker.ofDefaults("test-vnpay-query"));
    }

    private Payment payment() {
        return Payment.builder()
                .id(PAYMENT_ID)
                .provider(PaymentProvider.VNPAY)
                .amount(new BigDecimal("1234.50"))
                .checkoutUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"
                        + "?vnp_TxnRef=" + TXN_REF
                        + "&vnp_CreateDate=20261001120000&vnp_OrderInfo=Payment+order")
                .build();
    }

    private Map<String, String> signedResponse() {
        Map<String, String> response = new HashMap<>();
        response.put("vnp_ResponseId", "response1");
        response.put("vnp_Command", "querydr");
        response.put("vnp_ResponseCode", "00");
        response.put("vnp_Message", "Success");
        response.put("vnp_TmnCode", TMN_CODE);
        response.put("vnp_TxnRef", TXN_REF);
        response.put("vnp_Amount", "123450");
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261001120100");
        response.put("vnp_TransactionNo", "1234567");
        response.put("vnp_TransactionType", "01");
        response.put("vnp_TransactionStatus", "00");
        response.put("vnp_OrderInfo", "Payment order");
        response.put("vnp_PromotionCode", "");
        response.put("vnp_PromotionAmount", "");
        String data = String.join("|", response.get("vnp_ResponseId"), response.get("vnp_Command"),
                response.get("vnp_ResponseCode"), response.get("vnp_Message"), response.get("vnp_TmnCode"),
                response.get("vnp_TxnRef"), response.get("vnp_Amount"), response.get("vnp_BankCode"),
                response.get("vnp_PayDate"), response.get("vnp_TransactionNo"),
                response.get("vnp_TransactionType"), response.get("vnp_TransactionStatus"),
                response.get("vnp_OrderInfo"), response.get("vnp_PromotionCode"),
                response.get("vnp_PromotionAmount"));
        response.put("vnp_SecureHash", hmac(data));
        return response;
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
