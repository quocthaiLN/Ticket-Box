package com.ticketbox.api.module.payment.consumers;

import com.ticketbox.api.module.payment.domain.dtos.RefundProcessMessage;
import com.ticketbox.api.module.payment.services.RefundService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RefundProcessingConsumerTest {

    @Mock
    private RefundService refundService;

    private RefundProcessingConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RefundProcessingConsumer(refundService);
    }

    @Test
    @DisplayName("Should invoke refundService.processRefund with message refundId")
    void shouldInvokeProcessRefund() {
        UUID refundId = UUID.randomUUID();
        RefundProcessMessage message = new RefundProcessMessage(refundId);

        consumer.consumeRefundProcess(message);

        verify(refundService).processRefund(refundId);
    }

    @Test
    @DisplayName("Should gracefully handle null message or null refundId")
    void shouldHandleNullMessageGracefully() {
        consumer.consumeRefundProcess(null);
        consumer.consumeRefundProcess(new RefundProcessMessage(null));

        verifyNoInteractions(refundService);
    }

    @Test
    @DisplayName("Should rethrow exception when refundService fails")
    void shouldRethrowExceptionWhenServiceFails() {
        UUID refundId = UUID.randomUUID();
        RefundProcessMessage message = new RefundProcessMessage(refundId);

        doThrow(new RuntimeException("Refund processing error")).when(refundService).processRefund(refundId);

        assertThatThrownBy(() -> consumer.consumeRefundProcess(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Refund processing error");
    }
}
