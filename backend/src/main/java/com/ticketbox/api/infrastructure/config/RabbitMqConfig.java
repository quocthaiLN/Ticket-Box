package com.ticketbox.api.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RabbitMqConfig {

    @Bean(name = RabbitMqConstants.ARTIST_BIO_LISTENER_FACTORY)
    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory artistBioListenerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(2);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        // Khai báo converter
        rabbitTemplate.setMessageConverter(jsonMessageConverter);

        // Không hủy message không route được queue nào mà sẽ log ra
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(returned -> log.error(
        "RabbitMQ returned unroutable message: exchange={}, routingKey={}, replyText={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));    

        // Nếu Message không được broker nhận thì log lỗi
        rabbitTemplate.setConfirmCallback((correlation, acknowledged, cause) -> {
            if (!acknowledged) {
                log.error("RabbitMQ did not confirm message {}: {}", correlation, cause);
            }
        });

        return rabbitTemplate;
    }
}
