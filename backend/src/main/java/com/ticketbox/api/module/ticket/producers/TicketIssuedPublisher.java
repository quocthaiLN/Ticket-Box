package com.ticketbox.api.module.ticket.producers;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.ticket.events.TicketIssuedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class TicketIssuedPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(TicketIssuedEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConstants.TICKET_EXCHANGE,
                RabbitMqConstants.TICKET_ISSUED_ROUTING_KEY, event);
    }
}
