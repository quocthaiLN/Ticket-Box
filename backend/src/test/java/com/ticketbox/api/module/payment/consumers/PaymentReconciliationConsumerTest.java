package com.ticketbox.api.module.payment.consumers;

import com.ticketbox.api.module.payment.domain.dtos.PaymentReconcileMessage;
import com.ticketbox.api.module.payment.services.PaymentService;
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
class PaymentReconciliationConsumerTest {

    @Mock
    private PaymentService paymentService;

    private PaymentReconciliationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentReconciliationConsumer(paymentService);
    }

    @Test
    @DisplayName("Should invoke paymentService.reconcilePayment with message paymentId")
    void shouldInvokeReconcilePayment() {
        UUID paymentId = UUID.randomUUID();
        PaymentReconcileMessage message = new PaymentReconcileMessage(paymentId);

        consumer.consumeReconciliationCheck(message);

        verify(paymentService).reconcilePayment(paymentId);
    }

    @Test
    @DisplayName("Should gracefully handle null message or null paymentId")
    void shouldHandleNullMessageGracefully() {
        consumer.consumeReconciliationCheck(null);
        consumer.consumeReconciliationCheck(new PaymentReconcileMessage(null));

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("Should rethrow exception when paymentService fails")
    void shouldRethrowExceptionWhenServiceFails() {
        UUID paymentId = UUID.randomUUID();
        PaymentReconcileMessage message = new PaymentReconcileMessage(paymentId);

        doThrow(new RuntimeException("Service error")).when(paymentService).reconcilePayment(paymentId);

        assertThatThrownBy(() -> consumer.consumeReconciliationCheck(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Service error");
    }
}
