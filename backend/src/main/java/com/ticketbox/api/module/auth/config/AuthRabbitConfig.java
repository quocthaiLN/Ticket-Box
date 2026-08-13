package com.ticketbox.api.module.auth.config;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthRabbitConfig {

    @Bean
    public TopicExchange authExchange() {
        return new TopicExchange(RabbitMqConstants.AUTH_EXCHANGE, true, false);
    }

    @Bean
    public Queue authOtpQueue() {
        return new Queue(RabbitMqConstants.AUTH_OTP_QUEUE, true);
    }

    @Bean
    public Binding authOtpBinding(Queue authOtpQueue, TopicExchange authExchange) {
        return BindingBuilder
                .bind(authOtpQueue)
                .to(authExchange)
                .with(RabbitMqConstants.AUTH_OTP_ROUTING_KEY);
    }
}
