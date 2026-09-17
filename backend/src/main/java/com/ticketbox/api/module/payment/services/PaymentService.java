package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentRequest;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentResponse;
import com.ticketbox.api.module.payment.domain.dtos.PaymentCallbackResponse;
import com.ticketbox.api.module.payment.domain.dtos.PaymentResponse;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import java.util.Map;
import java.util.Optional;

public interface PaymentService {

    PaymentResponse getPayment(User currentUser, java.util.UUID paymentId);

    CreatePaymentResponse createPayment(User currentUser, String idempotencyKey, CreatePaymentRequest request,
            String clientIp);

    Optional<PaymentCallbackResponse> handleCallback(PaymentProvider provider, Map<String, String> parameters);
}
