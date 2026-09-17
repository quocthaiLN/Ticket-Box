package com.ticketbox.api.module.ticket.config;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketRabbitConfig {

    @Bean
    public TopicExchange ticketExchange() {
        return new TopicExchange(RabbitMqConstants.TICKET_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationTicketIssuedQueue() {
        return new Queue(RabbitMqConstants.NOTIFICATION_TICKET_ISSUED_QUEUE, true);
    }

    @Bean
    public Binding notificationTicketIssuedBinding(Queue notificationTicketIssuedQueue,
            TopicExchange ticketExchange) {
        return BindingBuilder.bind(notificationTicketIssuedQueue).to(ticketExchange)
                .with(RabbitMqConstants.TICKET_ISSUED_ROUTING_KEY);
    }
}
