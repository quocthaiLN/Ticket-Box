package com.ticketbox.api.module.catalog.config;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogRabbitConfig {

    @Bean
    public TopicExchange catalogExchange() {
        return new TopicExchange(RabbitMqConstants.CATALOG_EXCHANGE, true, false);
    }

    @Bean
    public Queue catalogCacheWarmupQueue() {
        return new Queue(RabbitMqConstants.CATALOG_CACHE_WARMUP_QUEUE, true);
    }

    @Bean
    public Binding catalogCacheWarmupBinding(Queue catalogCacheWarmupQueue, TopicExchange catalogExchange) {
        return BindingBuilder
                .bind(catalogCacheWarmupQueue)
                .to(catalogExchange)
                .with(RabbitMqConstants.CATALOG_CACHE_WARMUP_ROUTING_KEY);
    }
}
