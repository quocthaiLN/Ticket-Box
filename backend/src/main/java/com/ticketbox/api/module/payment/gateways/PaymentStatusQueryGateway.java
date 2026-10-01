package com.ticketbox.api.module.payment.gateways;

import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;

public interface PaymentStatusQueryGateway {
    PaymentProvider getProvider();

    GatewayQueryResult query(Payment payment);
}
