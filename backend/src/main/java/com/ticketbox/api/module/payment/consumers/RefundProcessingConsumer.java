package com.ticketbox.api.module.payment.consumers;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.payment.domain.dtos.RefundProcessMessage;
import com.ticketbox.api.module.payment.services.RefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RefundProcessingConsumer {

    private final RefundService refundService;

    public RefundProcessingConsumer(RefundService refundService) {
        this.refundService = refundService;
    }

    @RabbitListener(queues = RabbitMqConstants.PAYMENT_REFUND_PROCESS_QUEUE)
    public void consumeRefundProcess(RefundProcessMessage message) {
        if (message == null || message.refundId() == null) {
            log.warn("Received empty refund process message");
            return;
        }

        log.info("Received refund processing message for refund {}", message.refundId());
        try {
            refundService.processRefund(message.refundId());
            log.info("Finished refund processing for refund {}", message.refundId());
        } catch (Exception exception) {
            log.error("Failed to process refund {}: {}", message.refundId(), exception.getMessage(), exception);
            throw exception;
        }
    }
}
