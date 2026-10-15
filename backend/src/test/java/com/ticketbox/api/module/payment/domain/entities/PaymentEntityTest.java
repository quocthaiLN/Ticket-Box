package com.ticketbox.api.module.payment.domain.entities;

import com.ticketbox.api.module.order.domain.entities.Order;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentEntityTest {

    @Test
    @DisplayName("Payment: default version is 0 and status is PENDING")
    void testDefaults() {
        Payment payment = Payment.builder().build();
        assertEquals(0L, payment.getVersion());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        assertEquals("VND", payment.getCurrency());
        assertFalse(payment.isRefundRequired());
    }

    @Test
    @DisplayName("Payment: failure message and payment URL helper accessors work properly")
    void testHelperAccessors() {
        Payment payment = Payment.builder().build();
        payment.setFailureMessage("Test failure message");
        payment.setPaymentUrl("https://payment.example.com/checkout");

        assertEquals("Test failure message", payment.getFailureReason());
        assertEquals("Test failure message", payment.getFailureMessage());
        assertEquals("https://payment.example.com/checkout", payment.getCheckoutUrl());
        assertEquals("https://payment.example.com/checkout", payment.getPaymentUrl());
    }

    @Test
    @DisplayName("Payment: failure code field can be set and retrieved")
    void testFailureCode() {
        Payment payment = Payment.builder()
                .failureCode("GATEWAY_TIMEOUT")
                .build();
        assertEquals("GATEWAY_TIMEOUT", payment.getFailureCode());
    }
}
