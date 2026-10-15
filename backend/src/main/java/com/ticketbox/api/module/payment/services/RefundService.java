package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.module.payment.domain.dtos.RefundResponse;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.Refund;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RefundService {

    Refund createRefund(Payment payment, BigDecimal amount, String reason, String idempotencyKey, UUID requestedBy);

    RefundResponse processRefund(UUID refundId);

    List<RefundResponse> getRefundsByPaymentId(UUID paymentId);
}
