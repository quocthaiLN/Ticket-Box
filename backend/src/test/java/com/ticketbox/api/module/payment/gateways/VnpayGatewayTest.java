package com.ticketbox.api.module.payment.gateways;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketbox.api.module.payment.domain.dtos.CallbackHandlingResult;
import com.ticketbox.api.module.payment.domain.dtos.GatewayCallback;
import com.ticketbox.api.module.payment.domain.dtos.PaymentGatewayRequest;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class VnpayGatewayTest {

    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String HASH_SECRET = "test-secret";

    @Test
    void createsPaymentUrlUsingConfiguredDefaults() {
        VnpayGateway gateway = new VnpayGateway(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "TEST_CODE",
                HASH_SECRET,
                "https://frontend.example.test/payment/return");

        String paymentUrl = gateway.createPaymentUrl(PaymentGatewayRequest.builder()
                .orderId(UUID.fromString("11111111-1111-4111-8111-111111111111"))
                .amount(new BigDecimal("1234.50"))
                .txnRef("txn-123")
                .build());

        URI uri = URI.create(paymentUrl);
        Map<String, String> params = parseQuery(uri.getRawQuery());

        assertThat(uri.getScheme() + "://" + uri.getAuthority() + uri.getPath())
                .isEqualTo("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        assertThat(params)
                .containsEntry("vnp_Version", "2.1.0")
                .containsEntry("vnp_Command", "pay")
                .containsEntry("vnp_TmnCode", "TEST_CODE")
                .containsEntry("vnp_Amount", "123450")
                .containsEntry("vnp_CurrCode", "VND")
                .containsEntry("vnp_OrderType", "other")
                .containsEntry("vnp_Locale", "vn")
                .containsEntry("vnp_IpAddr", "127.0.0.1")
                .containsEntry("vnp_ReturnUrl", "https://frontend.example.test/payment/return")
                .containsEntry("vnp_TxnRef", "txn-123")
                .containsKey("vnp_SecureHash");
        assertThat(params).doesNotContainKey("vnp_SecureHashType");

        LocalDateTime createDate = LocalDateTime.parse(params.get("vnp_CreateDate"), VNPAY_DATE_FORMAT);
        LocalDateTime expireDate = LocalDateTime.parse(params.get("vnp_ExpireDate"), VNPAY_DATE_FORMAT);
        assertThat(expireDate).isEqualTo(createDate.plusMinutes(15));
    }

    @Test
    void verifiesSignedSuccessfulCallback() {
        VnpayGateway gateway = gateway();
        Map<String, String> callbackParams = signedCallbackParams();

        GatewayCallback result = gateway.verifyCallback(callbackParams);

        assertThat(result.signatureValid()).isTrue();
        assertThat(result.targetStatus()).contains(PaymentStatus.SUCCEEDED);
        assertThat(result.paymentId()).contains(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        assertThat(result.providerTransactionId()).isEqualTo("vnp-456");
        assertThat(result.amount()).contains(new BigDecimal("1234.50"));
        assertThat(gateway.getProvider()).isEqualTo(PaymentProvider.VNPAY);
        assertThat(gateway.transactionReference(UUID.fromString("11111111-1111-4111-8111-111111111111")))
                .isEqualTo("11111111111141118111111111111111");
        assertThat(gateway.responseFor(CallbackHandlingResult.INVALID_SIGNATURE))
                .hasValueSatisfying(response -> assertThat(response.responseCode()).isEqualTo("97"));
    }

    @Test
    void rejectsCallbackWhenSignedDataIsTampered() {
        Map<String, String> callbackParams = signedCallbackParams();
        callbackParams.put("vnp_Amount", "999900");

        GatewayCallback result = gateway().verifyCallback(callbackParams);

        assertThat(result.signatureValid()).isFalse();
        assertThat(result.targetStatus()).contains(PaymentStatus.SUCCEEDED);
    }

    private Map<String, String> parseQuery(String rawQuery) {
        return Arrays.stream(rawQuery.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }

    private VnpayGateway gateway() {
        return new VnpayGateway(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "TEST_CODE",
                HASH_SECRET,
                "https://frontend.example.test/payment/return");
    }

    private Map<String, String> signedCallbackParams() {
        Map<String, String> params = new TreeMap<>(Map.of(
                "vnp_Amount", "123450",
                "vnp_OrderInfo", "Payment for ticket",
                "vnp_ResponseCode", "00",
                "vnp_TransactionNo", "vnp-456",
                "vnp_TransactionStatus", "00",
                "vnp_TmnCode", "TEST_CODE",
                "vnp_TxnRef", "11111111111141118111111111111111"));
        params.put("vnp_SecureHash", secureHash(params));
        params.put("vnp_SecureHashType", "HmacSHA512");
        return params;
    }

    private String secureHash(Map<String, String> params) {
        String hashData = params.entrySet().stream()
                .filter(entry -> !"vnp_SecureHash".equals(entry.getKey()))
                .filter(entry -> !"vnp_SecureHashType".equals(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));

        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(HASH_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return toHex(mac.doFinal(hashData.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
