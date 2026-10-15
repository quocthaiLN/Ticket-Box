package com.ticketbox.api.module.payment.consumers;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.payment.domain.dtos.PaymentReconcileMessage;
import com.ticketbox.api.module.payment.services.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentReconciliationConsumer {

    private final PaymentService paymentService;

    public PaymentReconciliationConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = RabbitMqConstants.PAYMENT_RECONCILE_QUEUE)
    public void consumeReconciliationCheck(PaymentReconcileMessage message) {
        if (message == null || message.paymentId() == null) {
            log.warn("Received empty payment reconciliation message");
            return;
        }

        log.info("Received payment reconciliation message for payment {}", message.paymentId());
        try {
            paymentService.reconcilePayment(message.paymentId());
            log.info("Finished payment reconciliation for payment {}", message.paymentId());
        } catch (Exception exception) {
            log.error("Failed to reconcile payment {}: {}", message.paymentId(), exception.getMessage(), exception);
            throw exception;
        }
    }
}
