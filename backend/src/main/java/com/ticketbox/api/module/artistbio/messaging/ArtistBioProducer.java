package com.ticketbox.api.module.artistbio.messaging;

import static com.ticketbox.api.infrastructure.config.RabbitMqConstants.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.stereotype.Component;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@Component
@RequiredArgsConstructor
public class ArtistBioProducer {
    private final RabbitTemplate rabbitTemplate;
    public void generate(UUID jobId) { send(jobId, ARTIST_BIO_EXCHANGE, ARTIST_BIO_ROUTING_KEY, false); }
    public void retry(UUID jobId) { send(jobId, ARTIST_BIO_EXCHANGE, ARTIST_BIO_RETRY_KEY, true); }
    public void dead(UUID jobId) { send(jobId, ARTIST_BIO_DLX, ARTIST_BIO_DEAD_KEY, true); }

    private void send(UUID jobId, String exchange, String key, boolean waitForConfirm) {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(exchange, key, new ArtistBioMessage(jobId), message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        }, correlation);
        if (!waitForConfirm) return;
        try {
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("Artist bio message was not routed and confirmed");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Artist bio publish interrupted", exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("Artist bio publish not confirmed", exception);
        }
    }
}
