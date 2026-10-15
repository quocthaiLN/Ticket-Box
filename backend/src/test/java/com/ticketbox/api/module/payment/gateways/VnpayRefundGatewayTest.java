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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
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

class VnpayRefundGatewayTest {
    private static final String REFUND_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private static final String TMN_CODE = "TESTCODE";
    private static final String SECRET = "test-vnpay-hash-secret";
    private static final UUID REFUND_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String REQUEST_ID = "33333333333343338333333333333333";
    private static final String TXN_REF = "TXN_987654";
    private static final String TRANS_NO = "7654321";
    private static final String TRANS_DATE = "20261001120000";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void providerReturnsVnpay() {
        VnpayRefundGateway gateway = gateway(RestClient.builder().build(), CircuitBreaker.ofDefaults("test"));
        assertThat(gateway.getProvider()).isEqualTo(PaymentProvider.VNPAY);
    }

    @Test
    void signsRefundAndAcceptsSuccessfulResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VnpayRefundGateway gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("test-vnpay-refund"));

        Map<String, String> responseMap = signedResponse("00", "00");

        server.expect(once(), requestTo(REFUND_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    JsonNode body = objectMapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.get("vnp_RequestId").asText()).isEqualTo(REQUEST_ID);
                    assertThat(body.get("vnp_Version").asText()).isEqualTo("2.1.0");
                    assertThat(body.get("vnp_Command").asText()).isEqualTo("refund");
                    assertThat(body.get("vnp_TmnCode").asText()).isEqualTo(TMN_CODE);
                    assertThat(body.get("vnp_TransactionType").asText()).isEqualTo("02");
                    assertThat(body.get("vnp_TxnRef").asText()).isEqualTo(TXN_REF);
                    assertThat(body.get("vnp_Amount").asText()).isEqualTo("50000000"); // 500,000 * 100
                    assertThat(body.get("vnp_TransactionNo").asText()).isEqualTo(TRANS_NO);
                    assertThat(body.get("vnp_TransactionDate").asText()).isEqualTo(TRANS_DATE);
                    assertThat(body.get("vnp_CreateBy").asText()).isEqualTo("TicketBox");

                    String signedData = String.join("|",
                            REQUEST_ID,
                            "2.1.0",
                            "refund",
                            TMN_CODE,
                            "02",
                            TXN_REF,
                            "50000000",
                            TRANS_NO,
                            TRANS_DATE,
                            "TicketBox",
                            body.get("vnp_CreateDate").asText(),
                            "127.0.0.1",
                            "Late refund after hold expiry");
                    assertThat(body.get("vnp_SecureHash").asText()).isEqualTo(hmac(signedData));

                    return withSuccess(objectMapper.writeValueAsString(responseMap), MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        RefundGatewayRequest request = RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(TXN_REF)
                .providerTxnId(TRANS_NO)
                .amount(new BigDecimal("500000"))
                .reason("Late refund after hold expiry")
                .transDate(TRANS_DATE)
                .build();

        RefundGatewayResult result = gateway.refund(request);

        assertThat(result.status()).isEqualTo(RefundStatus.SUCCESS);
        assertThat(result.providerRefundId()).isEqualTo("REFUND_TRANS_123");
        assertThat(result.responseCode()).isEqualTo("00");
        assertThat(result.message()).isEqualTo("Refund successful");
        server.verify();
    }

    @Test
    void rejectsTamperedSignedResponse() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VnpayRefundGateway gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("test-vnpay-tampered"));

        Map<String, String> responseMap = signedResponse("00", "00");
        responseMap.put("vnp_Amount", "99999999"); // Tampered amount without updating hash

        server.expect(once(), requestTo(REFUND_URL))
                .andRespond(withSuccess(objectMapper.writeValueAsString(responseMap), MediaType.APPLICATION_JSON));

        RefundGatewayRequest request = RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(TXN_REF)
                .amount(new BigDecimal("500000"))
                .build();

        assertThatThrownBy(() -> gateway.refund(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
        server.verify();
    }

    @Test
    void handlesUnsignedDuplicateRequestResponseAsProcessing() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VnpayRefundGateway gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("test-vnpay-dup"));

        Map<String, String> responseMap = new HashMap<>();
        responseMap.put("vnp_ResponseCode", "94");
        responseMap.put("vnp_Message", "Request is duplicated");

        server.expect(once(), requestTo(REFUND_URL))
                .andRespond(withSuccess(objectMapper.writeValueAsString(responseMap), MediaType.APPLICATION_JSON));

        RefundGatewayRequest request = RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(TXN_REF)
                .amount(new BigDecimal("500000"))
                .build();

        RefundGatewayResult result = gateway.refund(request);

        assertThat(result.status()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(result.responseCode()).isEqualTo("94");
        assertThat(result.message()).isEqualTo("Request is duplicated");
        server.verify();
    }

    @Test
    void rejectsSuccessResponseWithoutSecureHash() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VnpayRefundGateway gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("test-vnpay-unsigned-success"));

        Map<String, String> responseMap = new HashMap<>();
        responseMap.put("vnp_ResponseCode", "00");
        responseMap.put("vnp_Message", "Refund success");

        server.expect(once(), requestTo(REFUND_URL))
                .andRespond(withSuccess(objectMapper.writeValueAsString(responseMap), MediaType.APPLICATION_JSON));

        RefundGatewayRequest request = RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(TXN_REF)
                .amount(new BigDecimal("500000"))
                .build();

        assertThatThrownBy(() -> gateway.refund(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "91, '', PROCESSING, 91",
            "94, '', PROCESSING, 94",
            "00, '01', PROCESSING, 00",
            "00, '05', PROCESSING, 00",
            "00, '06', PROCESSING, 00",
            "00, '02', FAILED, TXN_STATUS_02",
            "00, '09', FAILED, TXN_STATUS_09",
            "99, '', FAILED, 99",
            "02, '', FAILED, 02"
    })
    void mapsResponseCodesCorrectly(String responseCode, String transactionStatus, RefundStatus expectedStatus, String expectedResolvedCode) throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VnpayRefundGateway gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("test-vnpay-mapping"));

        Map<String, String> responseMap = signedResponse(responseCode, transactionStatus);

        server.expect(once(), requestTo(REFUND_URL))
                .andRespond(withSuccess(objectMapper.writeValueAsString(responseMap), MediaType.APPLICATION_JSON));

        RefundGatewayRequest request = RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(TXN_REF)
                .amount(new BigDecimal("500000"))
                .build();

        RefundGatewayResult result = gateway.refund(request);

        assertThat(result.status()).isEqualTo(expectedStatus);
        assertThat(result.responseCode()).isEqualTo(expectedResolvedCode);
        server.verify();
    }

    @Test
    void handlesNetworkTimeoutGracefullyWithProcessingStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        VnpayRefundGateway gateway = gateway(builder.build(), CircuitBreaker.ofDefaults("test-vnpay-timeout"));

        server.expect(once(), requestTo(REFUND_URL))
                .andRespond(request -> {
                    throw new ResourceAccessException("Connection timed out");
                });

        RefundGatewayRequest request = RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(TXN_REF)
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
        CircuitBreaker breaker = CircuitBreaker.of("test-vnpay-cb", CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(1)
                .slidingWindowSize(1)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .recordException(exception -> exception instanceof ResourceAccessException)
                .build());

        VnpayRefundGateway gateway = gateway(builder.build(), breaker);

        server.expect(once(), requestTo(REFUND_URL))
                .andRespond(request -> {
                    throw new ResourceAccessException("Connection timed out");
                });

        RefundGatewayRequest request = RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(TXN_REF)
                .amount(new BigDecimal("500000"))
                .build();

        RefundGatewayResult firstResult = gateway.refund(request);
        assertThat(firstResult.status()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(firstResult.responseCode()).isEqualTo("GATEWAY_TIMEOUT");

        // Second call while OPEN
        RefundGatewayResult secondResult = gateway.refund(request);
        assertThat(secondResult.status()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(secondResult.responseCode()).isEqualTo("CIRCUIT_BREAKER_OPEN");
        server.verify();
    }

    @Test
    void validatesRequiredInputs() {
        VnpayRefundGateway gateway = gateway(RestClient.builder().build(), CircuitBreaker.ofDefaults("test"));

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
                .hasMessageContaining("Merchant transaction reference is required");

        assertThatThrownBy(() -> gateway.refund(RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef("   ")
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Merchant transaction reference is required");

        assertThatThrownBy(() -> gateway.refund(RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(TXN_REF)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund amount must be positive");

        assertThatThrownBy(() -> gateway.refund(RefundGatewayRequest.builder()
                .refundId(REFUND_ID)
                .merchantTxnRef(TXN_REF)
                .amount(BigDecimal.ZERO)
                .build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refund amount must be positive");
    }

    private VnpayRefundGateway gateway(RestClient restClient, CircuitBreaker breaker) {
        return new VnpayRefundGateway(restClient, REFUND_URL, TMN_CODE, SECRET, "127.0.0.1", breaker);
    }

    private Map<String, String> signedResponse(String responseCode, String transactionStatus) {
        Map<String, String> response = new HashMap<>();
        response.put("vnp_ResponseId", "RESP_001");
        response.put("vnp_Command", "refund");
        response.put("vnp_ResponseCode", responseCode);
        response.put("vnp_Message", "Refund successful");
        response.put("vnp_TmnCode", TMN_CODE);
        response.put("vnp_TxnRef", TXN_REF);
        response.put("vnp_Amount", "50000000");
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261001120500");
        response.put("vnp_TransactionNo", "REFUND_TRANS_123");
        response.put("vnp_TransactionType", "02");
        response.put("vnp_TransactionStatus", transactionStatus != null ? transactionStatus : "");
        response.put("vnp_OrderInfo", "Refund order " + TXN_REF);

        String data = String.join("|",
                response.get("vnp_ResponseId"),
                response.get("vnp_Command"),
                response.get("vnp_ResponseCode"),
                response.get("vnp_Message"),
                response.get("vnp_TmnCode"),
                response.get("vnp_TxnRef"),
                response.get("vnp_Amount"),
                response.get("vnp_BankCode"),
                response.get("vnp_PayDate"),
                response.get("vnp_TransactionNo"),
                response.get("vnp_TransactionType"),
                response.get("vnp_TransactionStatus"),
                response.get("vnp_OrderInfo"));
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
