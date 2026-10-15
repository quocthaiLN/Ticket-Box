package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.payment.domain.dtos.PaymentReconcileMessage;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.repositories.PaymentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationSchedulerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PaymentReconciliationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PaymentReconciliationScheduler(paymentRepository, rabbitTemplate);
    }

    @Test
    @DisplayName("Should scan due payments for INITIATED, CREATING, PENDING and publish reconcile message")
    void shouldScanAndPublishReconcileMessages() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(paymentRepository.findDueIds(any(LocalDateTime.class), eq(List.of(PaymentStatus.INITIATED, PaymentStatus.CREATING, PaymentStatus.PENDING)), eq(PageRequest.of(0, 50))))
                .thenReturn(List.of(id1, id2));

        scheduler.reconcileDuePayments();

        var messageCaptor = ArgumentCaptor.forClass(PaymentReconcileMessage.class);
        verify(rabbitTemplate, times(2)).convertAndSend(
                eq(RabbitMqConstants.PAYMENT_EXCHANGE),
                eq(RabbitMqConstants.PAYMENT_RECONCILE_ROUTING_KEY),
                messageCaptor.capture());

        List<PaymentReconcileMessage> captured = messageCaptor.getAllValues();
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).paymentId()).isEqualTo(id1);
        assertThat(captured.get(1).paymentId()).isEqualTo(id2);
    }

    @Test
    @DisplayName("Should continue processing when publishing one message fails")
    void shouldContinueWhenPublishFailsForOnePayment() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(paymentRepository.findDueIds(any(LocalDateTime.class), any(), any()))
                .thenReturn(List.of(id1, id2));

        doThrow(new RuntimeException("Broker connection error"))
                .when(rabbitTemplate).convertAndSend(
                        eq(RabbitMqConstants.PAYMENT_EXCHANGE),
                        eq(RabbitMqConstants.PAYMENT_RECONCILE_ROUTING_KEY),
                        eq(new PaymentReconcileMessage(id1)));

        scheduler.reconcileDuePayments();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConstants.PAYMENT_EXCHANGE),
                eq(RabbitMqConstants.PAYMENT_RECONCILE_ROUTING_KEY),
                eq(new PaymentReconcileMessage(id2)));
    }
}
