package com.ticketbox.api.module.payment.gateways;

import com.ticketbox.api.module.payment.domain.dtos.CallbackHandlingResult;
import com.ticketbox.api.module.payment.domain.dtos.GatewayCallback;
import com.ticketbox.api.module.payment.domain.dtos.PaymentCallbackResponse;
import com.ticketbox.api.module.payment.domain.dtos.PaymentGatewayRequest;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PaymentGatewayStrategy {
    String createPaymentUrl(PaymentGatewayRequest request);

    String transactionReference(UUID paymentId);

    GatewayCallback verifyCallback(Map<String, String> params);

    Optional<PaymentCallbackResponse> responseFor(CallbackHandlingResult result);

    PaymentProvider getProvider();
}
