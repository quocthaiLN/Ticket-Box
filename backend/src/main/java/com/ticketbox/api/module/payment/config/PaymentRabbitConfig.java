package com.ticketbox.api.module.payment.config;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentRabbitConfig {

    @Bean
    public TopicExchange paymentExchange() {
        return new TopicExchange(RabbitMqConstants.PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue ticketPaymentSucceededQueue() {
        return new Queue(RabbitMqConstants.TICKET_PAYMENT_SUCCEEDED_QUEUE, true);
    }

    @Bean
    public Queue notificationPaymentFailedQueue() {
        return new Queue(RabbitMqConstants.NOTIFICATION_PAYMENT_FAILED_QUEUE, true);
    }

    @Bean
    public Binding ticketPaymentSucceededBinding(Queue ticketPaymentSucceededQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(ticketPaymentSucceededQueue).to(paymentExchange)
                .with(RabbitMqConstants.PAYMENT_SUCCEEDED_ROUTING_KEY);
    }

    @Bean
    public Binding notificationPaymentFailedBinding(Queue notificationPaymentFailedQueue,
            TopicExchange paymentExchange) {
        return BindingBuilder.bind(notificationPaymentFailedQueue).to(paymentExchange)
                .with(RabbitMqConstants.PAYMENT_FAILED_ROUTING_KEY);
    }

    @Bean
    public Queue paymentReconcileQueue() {
        return new Queue(RabbitMqConstants.PAYMENT_RECONCILE_QUEUE, true);
    }

    @Bean
    public Queue paymentRefundProcessQueue() {
        return new Queue(RabbitMqConstants.PAYMENT_REFUND_PROCESS_QUEUE, true);
    }

    @Bean
    public Binding paymentReconcileBinding(Queue paymentReconcileQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentReconcileQueue).to(paymentExchange)
                .with(RabbitMqConstants.PAYMENT_RECONCILE_ROUTING_KEY);
    }

    @Bean
    public Binding paymentRefundProcessBinding(Queue paymentRefundProcessQueue, TopicExchange paymentExchange) {
        return BindingBuilder.bind(paymentRefundProcessQueue).to(paymentExchange)
                .with(RabbitMqConstants.PAYMENT_REFUND_PROCESS_ROUTING_KEY);
    }
}
