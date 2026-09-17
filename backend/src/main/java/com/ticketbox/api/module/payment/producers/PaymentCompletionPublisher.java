package com.ticketbox.api.module.payment.producers;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentCompletionPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(PaymentCompletedEvent event) {
        String routingKey = event.status() == PaymentStatus.SUCCEEDED
                ? RabbitMqConstants.PAYMENT_SUCCEEDED_ROUTING_KEY
                : RabbitMqConstants.PAYMENT_FAILED_ROUTING_KEY;
        rabbitTemplate.convertAndSend(RabbitMqConstants.PAYMENT_EXCHANGE, routingKey, event);
    }
}
