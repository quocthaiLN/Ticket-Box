package com.ticketbox.api.module.payment.gateways;

import com.ticketbox.api.module.payment.domain.dtos.CallbackHandlingResult;
import com.ticketbox.api.module.payment.domain.dtos.GatewayCallback;
import com.ticketbox.api.module.payment.domain.dtos.PaymentGatewayRequest;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MomoGatewayTest {

    private static final String CREATE_URL = "https://test-payment.momo.vn/v2/gateway/api/create";
    private static final String PARTNER_CODE = "MOMO_TEST";
    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String REDIRECT_URL = "http://localhost:3001/payment/return";
    private static final String IPN_URL = "https://example.invalid/payments/momo/ipn";

    @Test
    void createsCaptureWalletPaymentAndReturnsPayUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MomoGateway gateway = gateway(builder.build());
        String paymentId = "11111111-1111-4111-8111-111111111111";

        server.expect(once(), requestTo(CREATE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.partnerCode").value(PARTNER_CODE))
                .andExpect(jsonPath("$.orderId").value(paymentId))
                .andExpect(jsonPath("$.requestId").value(paymentId))
                .andExpect(jsonPath("$.amount").value(1000))
                .andExpect(jsonPath("$.requestType").value("captureWallet"))
                .andExpect(jsonPath("$.extraData").value(""))
                .andRespond(withSuccess("""
                        {"resultCode":0,"message":"Successful.","payUrl":"https://payment.example.test/pay"}
                        """, MediaType.APPLICATION_JSON));

        String payUrl = gateway.createPaymentUrl(request(paymentId));

        assertThat(payUrl).isEqualTo("https://payment.example.test/pay");
        server.verify();
    }

    @Test
    void timeoutIsUnknownAndRetryUsesTheSameRequestId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MomoGateway gateway = gateway(builder.build());
        String paymentId = "11111111-1111-4111-8111-111111111111";

        server.expect(once(), requestTo(CREATE_URL))
                .andExpect(jsonPath("$.requestId").value(paymentId))
                .andRespond(request -> { throw new ResourceAccessException("Read timed out"); });
        server.expect(once(), requestTo(CREATE_URL))
                .andExpect(jsonPath("$.requestId").value(paymentId))
                .andRespond(withSuccess("""
                        {"resultCode":0,"payUrl":"https://payment.example.test/pay"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.createPaymentUrl(request(paymentId)))
                .isInstanceOf(MomoGateway.CreateOutcomeUnknownException.class)
                .hasCauseInstanceOf(ResourceAccessException.class);
        assertThat(gateway.createPaymentUrl(request(paymentId))).isEqualTo("https://payment.example.test/pay");
        server.verify();
    }

    @Test
    void serverErrorsOpenTheCircuitAndPreventAnotherRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MomoGateway gateway = gateway(builder.build());
        PaymentGatewayRequest request = request("11111111-1111-4111-8111-111111111111");
        for (int attempt = 0; attempt < 5; attempt++) {
            server.expect(once(), requestTo(CREATE_URL)).andRespond(withServerError());
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> gateway.createPaymentUrl(request))
                    .isInstanceOf(MomoGateway.CreateOutcomeUnknownException.class)
                    .hasCauseInstanceOf(HttpServerErrorException.class);
        }
        assertThatThrownBy(() -> gateway.createPaymentUrl(request))
                .isInstanceOf(MomoGateway.CreateOutcomeUnknownException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
        server.verify();
    }

    @Test
    void definitiveRejectionIsNotTreatedAsUnknown() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MomoGateway gateway = gateway(builder.build());
        server.expect(once(), requestTo(CREATE_URL)).andRespond(withSuccess("""
                {"resultCode":1002,"message":"Payment rejected"}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.createPaymentUrl(request("11111111-1111-4111-8111-111111111111")))
                .isInstanceOf(MomoGateway.CreateRejectedException.class);
        server.verify();
    }

    @Test
    void duplicateOrderCodeRemainsUnknownForReconciliation() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MomoGateway gateway = gateway(builder.build());
        server.expect(once(), requestTo(CREATE_URL)).andRespond(withSuccess("""
                {"resultCode":41,"message":"Duplicate orderId"}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.createPaymentUrl(request("11111111-1111-4111-8111-111111111111")))
                .isInstanceOf(MomoGateway.CreateOutcomeUnknownException.class);
        server.verify();
    }

    @Test
    void verifiesSignedIpnAndRejectsTamperedAmount() {
        MomoGateway gateway = gateway(RestClient.create());
        Map<String, String> ipn = signedIpn();

        GatewayCallback valid = gateway.verifyCallback(ipn);
        assertThat(valid.signatureValid()).isTrue();
        assertThat(valid.paymentId()).contains(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        assertThat(valid.providerTransactionId()).isEqualTo("4088878653");
        assertThat(valid.targetStatus()).contains(PaymentStatus.SUCCEEDED);
        assertThat(valid.amount()).contains(new BigDecimal("1000"));
        assertThat(valid.sanitizedPayload()).doesNotContainKey("signature");
        assertThat(gateway.getProvider()).isEqualTo(PaymentProvider.MOMO);
        assertThat(gateway.transactionReference(UUID.fromString("11111111-1111-4111-8111-111111111111")))
                .isEqualTo("11111111-1111-4111-8111-111111111111");
        assertThat(gateway.responseFor(CallbackHandlingResult.PROCESSED)).isEmpty();

        ipn.put("amount", "2000");
        assertThat(gateway.verifyCallback(ipn).signatureValid()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"1003,CANCELLED", "1006,CANCELLED", "1017,CANCELLED",
            "1001,FAILED", "0,SUCCEEDED", "1000,PENDING"})
    void mapsSignedOutcomesWithEmptyPayType(String code, PaymentStatus expected) {
        MomoGateway gateway = gateway(RestClient.create());
        Map<String, String> ipn = signedIpn();
        ipn.put("resultCode", code);
        ipn.put("payType", "");
        ipn.put("signature", hmacSha256(callbackData(ipn)));
        GatewayCallback result = gateway.verifyCallback(ipn);
        assertThat(result.signatureValid()).isTrue();
        assertThat(result.targetStatus()).contains(expected);
        if (expected == PaymentStatus.CANCELLED || expected == PaymentStatus.FAILED) {
            assertThat(result.failureReason()).contains("MOMO resultCode=" + code);
        }
        ipn.put("payType", "qr");
        assertThat(gateway.verifyCallback(ipn).signatureValid()).isFalse();
    }

    private MomoGateway gateway(RestClient restClient) {
        return new MomoGateway(restClient, CREATE_URL, PARTNER_CODE, ACCESS_KEY, SECRET_KEY, REDIRECT_URL, IPN_URL);
    }

    private PaymentGatewayRequest request(String paymentId) {
        return PaymentGatewayRequest.builder()
                .orderId(UUID.fromString("22222222-2222-4222-8222-222222222222"))
                .txnRef(paymentId)
                .amount(new BigDecimal("1000"))
                .orderInfo("Payment order " + paymentId)
                .build();
    }

    private Map<String, String> signedIpn() {
        Map<String, String> ipn = new LinkedHashMap<>();
        ipn.put("orderType", "momo_wallet");
        ipn.put("amount", "1000");
        ipn.put("partnerCode", PARTNER_CODE);
        ipn.put("orderId", "11111111-1111-4111-8111-111111111111");
        ipn.put("extraData", "");
        ipn.put("transId", "4088878653");
        ipn.put("responseTime", "1721720663942");
        ipn.put("resultCode", "0");
        ipn.put("message", "Successful.");
        ipn.put("payType", "qr");
        ipn.put("requestId", "request-123");
        ipn.put("orderInfo", "Payment order 11111111-1111-4111-8111-111111111111");
        ipn.put("signature", hmacSha256(callbackData(ipn)));
        return ipn;
    }

    private String callbackData(Map<String, String> params) {
        return "accessKey=" + ACCESS_KEY
                + "&amount=" + params.get("amount")
                + "&extraData=" + params.get("extraData")
                + "&message=" + params.get("message")
                + "&orderId=" + params.get("orderId")
                + "&orderInfo=" + params.get("orderInfo")
                + "&orderType=" + params.get("orderType")
                + "&partnerCode=" + params.get("partnerCode")
                + "&payType=" + params.get("payType")
                + "&requestId=" + params.get("requestId")
                + "&responseTime=" + params.get("responseTime")
                + "&resultCode=" + params.get("resultCode")
                + "&transId=" + params.get("transId");
    }

    private String hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
