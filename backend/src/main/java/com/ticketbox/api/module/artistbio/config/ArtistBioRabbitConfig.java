package com.ticketbox.api.module.artistbio.config;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.artistbio.services.ArtistBioJobStateService;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class ArtistBioRabbitConfig {
    @Bean
    public TopicExchange artistBioExchange() {
        return new TopicExchange(RabbitMqConstants.ARTIST_BIO_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange artistBioDeadLetterExchange() {
        return new DirectExchange(RabbitMqConstants.ARTIST_BIO_DLX, true, false);
    }

    @Bean
    public Queue artistBioQueue() {
        return QueueBuilder.durable(RabbitMqConstants.ARTIST_BIO_QUEUE)
                .deadLetterExchange(RabbitMqConstants.ARTIST_BIO_DLX)
                .deadLetterRoutingKey(RabbitMqConstants.ARTIST_BIO_DEAD_KEY)
                .build();
    }

    @Bean
    public Queue artistBioRetryQueue() {
        return QueueBuilder.durable(RabbitMqConstants.ARTIST_BIO_RETRY_QUEUE)
                .ttl(ArtistBioJobStateService.RETRY_SECONDS * 1000)
                .deadLetterExchange(RabbitMqConstants.ARTIST_BIO_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqConstants.ARTIST_BIO_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue artistBioDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMqConstants.ARTIST_BIO_DLQ).build();
    }

    @Bean
    public Binding artistBioBinding(@Qualifier("artistBioQueue") Queue artistBioQueue,
            @Qualifier("artistBioExchange") TopicExchange artistBioExchange) {
        return BindingBuilder.bind(artistBioQueue).to(artistBioExchange)
                .with(RabbitMqConstants.ARTIST_BIO_ROUTING_KEY);
    }

    @Bean
    public Binding artistBioRetryBinding(@Qualifier("artistBioRetryQueue") Queue artistBioRetryQueue,
            @Qualifier("artistBioExchange") TopicExchange artistBioExchange) {
        return BindingBuilder.bind(artistBioRetryQueue).to(artistBioExchange)
                .with(RabbitMqConstants.ARTIST_BIO_RETRY_KEY);
    }

    @Bean
    public Binding artistBioDeadLetterBinding(@Qualifier("artistBioDeadLetterQueue") Queue artistBioDeadLetterQueue,
            @Qualifier("artistBioDeadLetterExchange") DirectExchange artistBioDeadLetterExchange) {
        return BindingBuilder.bind(artistBioDeadLetterQueue).to(artistBioDeadLetterExchange)
                .with(RabbitMqConstants.ARTIST_BIO_DEAD_KEY);
    }
}
