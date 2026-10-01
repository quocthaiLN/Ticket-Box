package com.ticketbox.api.module.payment.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.payment.domain.dtos.CallbackHandlingResult;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentRequest;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentResponse;
import com.ticketbox.api.module.payment.domain.dtos.MomoIpnRequest;
import com.ticketbox.api.module.payment.domain.dtos.PaymentCallbackResponse;
import com.ticketbox.api.module.payment.domain.dtos.PaymentResponse;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.services.PaymentService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class PaymentControllerTest {

    @ParameterizedTest
    @MethodSource("momoIpnResults")
    void momoIpnAcknowledgesOnlyAppliedOrDuplicateCallbacks(CallbackHandlingResult result, HttpStatus expectedStatus) {
        FakePaymentService paymentService = new FakePaymentService(result);

        var actualStatus = new PaymentController(paymentService).momoIpn(ipn()).getStatusCode();

        assertThat(actualStatus).isEqualTo(expectedStatus);
        assertThat(paymentService.provider).isEqualTo(PaymentProvider.MOMO);
        assertThat(paymentService.parameters).containsEntry("orderId", "11111111-1111-4111-8111-111111111111")
                .containsEntry("amount", "1000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "null", "missing"})
    void validatesPayTypeThroughHttp(String input) throws Exception {
        PaymentService service = Mockito.mock(PaymentService.class);
        Mockito.lenient().when(service.handleCallbackResult(
                ArgumentMatchers.eq(PaymentProvider.MOMO), ArgumentMatchers.anyMap()))
                .thenReturn(CallbackHandlingResult.PROCESSED);
        try (var validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            var mvc = MockMvcBuilders
                    .standaloneSetup(new PaymentController(service)).setValidator(validator).build();
            var body = new LinkedHashMap<String, Object>(ipn().toParameters());
            if (input.equals("missing")) body.remove("payType");
            else body.put("payType", input.equals("empty") ? "" : null);
            mvc.perform(MockMvcRequestBuilders
                    .post("/payments/momo/ipn").contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(body)))
                    .andExpect(MockMvcResultMatchers.status()
                            .is(input.equals("empty") ? 204 : 400));
            if (input.equals("empty")) {
                Mockito.verify(service).handleCallbackResult(
                        ArgumentMatchers.eq(PaymentProvider.MOMO),
                        ArgumentMatchers.argThat(params -> "".equals(params.get("payType"))));
            } else {
                Mockito.verifyNoInteractions(service);
            }
        }
    }

    private static Stream<Arguments> momoIpnResults() {
        return Stream.of(
                Arguments.of(CallbackHandlingResult.PROCESSED, HttpStatus.NO_CONTENT),
                Arguments.of(CallbackHandlingResult.ALREADY_PROCESSED, HttpStatus.NO_CONTENT),
                Arguments.of(CallbackHandlingResult.INVALID_SIGNATURE, HttpStatus.BAD_REQUEST),
                Arguments.of(CallbackHandlingResult.PAYMENT_NOT_FOUND, HttpStatus.NOT_FOUND),
                Arguments.of(CallbackHandlingResult.AMOUNT_MISMATCH, HttpStatus.UNPROCESSABLE_ENTITY),
                Arguments.of(CallbackHandlingResult.CONFLICTING_RESULT, HttpStatus.CONFLICT));
    }

    private MomoIpnRequest ipn() {
        return new MomoIpnRequest(
                "momo_wallet", 1000L, "MOMO_TEST", "11111111-1111-4111-8111-111111111111", "", "signature",
                4088878653L, 1721720663942L, 0, "Successful.", "qr", "request-123", "Payment order");
    }

    private static class FakePaymentService implements PaymentService {
        private final CallbackHandlingResult result;
        private PaymentProvider provider;
        private Map<String, String> parameters;

        private FakePaymentService(CallbackHandlingResult result) {
            this.result = result;
        }

        @Override
        public CallbackHandlingResult handleCallbackResult(PaymentProvider provider, Map<String, String> parameters) {
            this.provider = provider;
            this.parameters = parameters;
            return result;
        }

        @Override
        public PaymentResponse getPayment(User currentUser, UUID paymentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CreatePaymentResponse createPayment(User currentUser, String idempotencyKey, CreatePaymentRequest request,
                String clientIp) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<PaymentCallbackResponse> handleCallback(PaymentProvider provider, Map<String, String> parameters) {
            throw new UnsupportedOperationException();
        }
    }
}
