package com.ticketbox.api.module.payment.domain.mappers;

import com.ticketbox.api.module.payment.domain.dtos.RefundResponse;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.Refund;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RefundMapperTest {

    private RefundMapper refundMapper;

    @BeforeEach
    void setUp() {
        refundMapper = new RefundMapperImpl();
    }

    @Test
    @DisplayName("RefundMapper: toResponse correctly maps all fields from Refund entity")
    void testToResponse() {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Payment payment = Payment.builder().id(paymentId).build();

        LocalDateTime now = LocalDateTime.now();
        Refund refund = Refund.builder()
                .id(refundId)
                .payment(payment)
                .amount(new BigDecimal("250000.00"))
                .currency("VND")
                .idempotencyKey("REFUND_TEST_KEY_123")
                .status(RefundStatus.REQUESTED)
                .providerRefundId("momo_rf_999")
                .reason("Late payment after hold expiry")
                .requestedBy(userId)
                .failureCode(null)
                .failureMessage(null)
                .requestedAt(now)
                .completedAt(now.plusMinutes(1))
                .build();

        RefundResponse response = refundMapper.toResponse(refund);

        assertNotNull(response);
        assertEquals(refundId, response.id());
        assertEquals(paymentId, response.paymentId());
        assertEquals(new BigDecimal("250000.00"), response.amount());
        assertEquals("VND", response.currency());
        assertEquals("REFUND_TEST_KEY_123", response.idempotencyKey());
        assertEquals(RefundStatus.REQUESTED, response.status());
        assertEquals("momo_rf_999", response.providerRefundId());
        assertEquals("Late payment after hold expiry", response.reason());
        assertEquals(userId, response.requestedBy());
        assertEquals(now, response.requestedAt());
        assertEquals(now.plusMinutes(1), response.completedAt());
    }

    @Test
    @DisplayName("RefundMapper: toResponse handles null refund gracefully")
    void testToResponse_nullRefund() {
        assertNull(refundMapper.toResponse(null));
    }
}
