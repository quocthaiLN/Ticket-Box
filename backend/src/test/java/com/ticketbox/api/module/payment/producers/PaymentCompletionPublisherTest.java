package com.ticketbox.api.module.payment.producers;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCompletionPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PaymentCompletionPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PaymentCompletionPublisher(rabbitTemplate);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"SUCCESS", "SUCCEEDED"})
    @DisplayName("Should publish to PAYMENT_SUCCEEDED_ROUTING_KEY for SUCCESS and SUCCEEDED")
    void shouldPublishToSucceededRoutingKey(PaymentStatus status) {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                paymentId, orderId, userId,
                PaymentProvider.VNPAY,
                status,
                BigDecimal.valueOf(100_000),
                "VND",
                "txn-123");

        publisher.publish(event);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConstants.PAYMENT_EXCHANGE),
                eq(RabbitMqConstants.PAYMENT_SUCCEEDED_ROUTING_KEY),
                eq(event));
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "EXPIRED", "CANCELLED", "INITIATED", "PENDING"})
    @DisplayName("Should publish to PAYMENT_FAILED_ROUTING_KEY for non-success statuses")
    void shouldPublishToFailedRoutingKey(PaymentStatus status) {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                paymentId, orderId, userId,
                PaymentProvider.VNPAY,
                status,
                BigDecimal.valueOf(100_000),
                "VND",
                "txn-123");

        publisher.publish(event);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConstants.PAYMENT_EXCHANGE),
                eq(RabbitMqConstants.PAYMENT_FAILED_ROUTING_KEY),
                eq(event));
    }
}
