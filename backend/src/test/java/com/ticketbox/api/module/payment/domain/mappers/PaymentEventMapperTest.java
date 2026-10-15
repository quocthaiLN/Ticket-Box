package com.ticketbox.api.module.payment.domain.mappers;

import com.ticketbox.api.module.payment.domain.dtos.PaymentEventResponse;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentEvent;
import com.ticketbox.api.module.payment.domain.entities.PaymentEventSource;
import com.ticketbox.api.module.payment.domain.entities.PaymentEventStatus;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentEventMapperTest {

    private PaymentEventMapper paymentEventMapper;

    @BeforeEach
    void setUp() {
        paymentEventMapper = new PaymentEventMapperImpl();
    }

    @Test
    @DisplayName("PaymentEventMapper: toResponse correctly maps all fields from PaymentEvent")
    void testToResponse() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder().id(paymentId).build();

        LocalDateTime now = LocalDateTime.now();
        PaymentEvent event = PaymentEvent.builder()
                .id(eventId)
                .payment(payment)
                .provider(PaymentProvider.MOMO)
                .eventSource(PaymentEventSource.IPN)
                .dedupeKey("MOMO_IPN_123456")
                .eventType("PAYMENT_SUCCESS")
                .rawPayload("{\"resultCode\":0}")
                .signatureValid(true)
                .processingStatus(PaymentEventStatus.PROCESSED)
                .errorMessage(null)
                .receivedAt(now)
                .processedAt(now.plusSeconds(2))
                .build();

        PaymentEventResponse response = paymentEventMapper.toResponse(event);

        assertNotNull(response);
        assertEquals(eventId, response.id());
        assertEquals(paymentId, response.paymentId());
        assertEquals(PaymentProvider.MOMO, response.provider());
        assertEquals(PaymentEventSource.IPN, response.eventSource());
        assertEquals("MOMO_IPN_123456", response.dedupeKey());
        assertEquals("PAYMENT_SUCCESS", response.eventType());
        assertTrue(response.signatureValid());
        assertEquals(PaymentEventStatus.PROCESSED, response.processingStatus());
        assertNull(response.errorMessage());
        assertEquals(now, response.receivedAt());
        assertEquals(now.plusSeconds(2), response.processedAt());
    }

    @Test
    @DisplayName("PaymentEventMapper: toResponse handles null event gracefully")
    void testToResponse_nullEvent() {
        assertNull(paymentEventMapper.toResponse(null));
    }
}
