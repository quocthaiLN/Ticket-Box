package com.ticketbox.api.module.payment.gateways;

import com.ticketbox.api.module.payment.domain.dtos.PaymentGatewayRequest;
import com.ticketbox.api.module.payment.domain.dtos.PaymentVerificationResult;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import java.util.Map;

public interface PaymentGatewayStrategy {
    String createPaymentUrl(PaymentGatewayRequest request);

    PaymentVerificationResult verifyCallback(Map<String, String> params);

    PaymentProvider getProvider();
}
