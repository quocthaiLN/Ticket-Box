package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.payment.domain.dtos.RefundProcessMessage;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import com.ticketbox.api.module.payment.repositories.RefundRepository;
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
class RefundProcessingSchedulerTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RefundProcessingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new RefundProcessingScheduler(refundRepository, rabbitTemplate);
    }

    @Test
    @DisplayName("Should scan pending refunds for REQUESTED, PROCESSING and publish message")
    void shouldScanAndPublishRefundMessages() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(refundRepository.findPendingRefundIds(eq(List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING)), eq(PageRequest.of(0, 50))))
                .thenReturn(List.of(id1, id2));

        scheduler.processPendingRefunds();

        var messageCaptor = ArgumentCaptor.forClass(RefundProcessMessage.class);
        verify(rabbitTemplate, times(2)).convertAndSend(
                eq(RabbitMqConstants.PAYMENT_EXCHANGE),
                eq(RabbitMqConstants.PAYMENT_REFUND_PROCESS_ROUTING_KEY),
                messageCaptor.capture());

        List<RefundProcessMessage> captured = messageCaptor.getAllValues();
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).refundId()).isEqualTo(id1);
        assertThat(captured.get(1).refundId()).isEqualTo(id2);
    }

    @Test
    @DisplayName("Should continue processing when publishing one refund message fails")
    void shouldContinueWhenPublishFailsForOneRefund() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(refundRepository.findPendingRefundIds(any(), any()))
                .thenReturn(List.of(id1, id2));

        doThrow(new RuntimeException("Rabbit error"))
                .when(rabbitTemplate).convertAndSend(
                        eq(RabbitMqConstants.PAYMENT_EXCHANGE),
                        eq(RabbitMqConstants.PAYMENT_REFUND_PROCESS_ROUTING_KEY),
                        eq(new RefundProcessMessage(id1)));

        scheduler.processPendingRefunds();

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConstants.PAYMENT_EXCHANGE),
                eq(RabbitMqConstants.PAYMENT_REFUND_PROCESS_ROUTING_KEY),
                eq(new RefundProcessMessage(id2)));
    }
}
