package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.payment.domain.dtos.PaymentCallbackResponse;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentRequest;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentResponse;
import com.ticketbox.api.module.payment.domain.dtos.MomoIpnRequest;
import com.ticketbox.api.module.payment.domain.dtos.PaymentResponse;
import java.util.Map;

public interface PaymentService {

    PaymentResponse getPayment(User currentUser, java.util.UUID paymentId);

    CreatePaymentResponse createPayment(User currentUser, String idempotencyKey, CreatePaymentRequest request,
            String clientIp);

    PaymentCallbackResponse handleVnpayIpn(Map<String, String> parameters);

    void handleMomoIpn(MomoIpnRequest request);
}
