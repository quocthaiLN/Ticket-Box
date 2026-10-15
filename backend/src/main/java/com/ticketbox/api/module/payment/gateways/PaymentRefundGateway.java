package com.ticketbox.api.module.payment.gateways;

import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayRequest;
import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayResult;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;

public interface PaymentRefundGateway {
    PaymentProvider getProvider();

    RefundGatewayResult refund(RefundGatewayRequest request);
}
