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

    // Tao Exchange chính
    @Bean
    public TopicExchange artistBioExchange() {
        return new TopicExchange(RabbitMqConstants.ARTIST_BIO_EXCHANGE, true, false);
    }

    // Tạo Dead Letter Exchange
    @Bean
    public DirectExchange artistBioDeadLetterExchange() {
        return new DirectExchange(RabbitMqConstants.ARTIST_BIO_DLX, true, false);
    }

    // Tạo Queue chính
    // Message bị lỗi/từ chối -> đưa vào DLX
    @Bean
    public Queue artistBioQueue() {
        return QueueBuilder.durable(RabbitMqConstants.ARTIST_BIO_QUEUE)
                .deadLetterExchange(RabbitMqConstants.ARTIST_BIO_DLX)
                .deadLetterRoutingKey(RabbitMqConstants.ARTIST_BIO_DEAD_KEY)
                .build();
    }

    // Tạo Retry Queue
    // Message bị lỗi/từ chối -> đưa ngược vào Exchange chính
    @Bean
    public Queue artistBioRetryQueue() {
        return QueueBuilder.durable(RabbitMqConstants.ARTIST_BIO_RETRY_QUEUE)
                .ttl(ArtistBioJobStateService.RETRY_SECONDS * 1000)
                .deadLetterExchange(RabbitMqConstants.ARTIST_BIO_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqConstants.ARTIST_BIO_ROUTING_KEY)
                .build();
    }

    // Dead Letter Queue
    @Bean
    public Queue artistBioDeadLetterQueue() {
        return QueueBuilder.durable(RabbitMqConstants.ARTIST_BIO_DLQ).build();
    }

    // Gắn Queue chính và Exchange chính
    @Bean
    public Binding artistBioBinding(@Qualifier("artistBioQueue") Queue artistBioQueue,
            @Qualifier("artistBioExchange") TopicExchange artistBioExchange) {
        return BindingBuilder.bind(artistBioQueue).to(artistBioExchange)
                .with(RabbitMqConstants.ARTIST_BIO_ROUTING_KEY);
    }

    // Gắn Retry Queue vào Exchange chính
    @Bean
    public Binding artistBioRetryBinding(@Qualifier("artistBioRetryQueue") Queue artistBioRetryQueue,
            @Qualifier("artistBioExchange") TopicExchange artistBioExchange) {
        return BindingBuilder.bind(artistBioRetryQueue).to(artistBioExchange)
                .with(RabbitMqConstants.ARTIST_BIO_RETRY_KEY);
    }

    // Gán Dead Letter Queue vào DLX
    @Bean
    public Binding artistBioDeadLetterBinding(@Qualifier("artistBioDeadLetterQueue") Queue artistBioDeadLetterQueue,
            @Qualifier("artistBioDeadLetterExchange") DirectExchange artistBioDeadLetterExchange) {
        return BindingBuilder.bind(artistBioDeadLetterQueue).to(artistBioDeadLetterExchange)
                .with(RabbitMqConstants.ARTIST_BIO_DEAD_KEY);
    }

    // Nguồn cung Message cho Retry Queue là Consumer đẩy ngược Message có count <= max vào Exchange chính
}
