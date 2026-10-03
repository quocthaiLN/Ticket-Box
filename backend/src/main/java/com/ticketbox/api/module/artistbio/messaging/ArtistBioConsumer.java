package com.ticketbox.api.module.artistbio.messaging;

import static com.ticketbox.api.infrastructure.config.RabbitMqConstants.*;
import com.rabbitmq.client.Channel;
import com.ticketbox.api.module.artistbio.services.ArtistBioProcessor;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

@Component
@Profile("worker")
@RequiredArgsConstructor
@Slf4j
public class ArtistBioConsumer {
    private final ArtistBioProcessor processor;
    private final ArtistBioProducer producer;

    @RabbitListener(queues = ARTIST_BIO_QUEUE, containerFactory = ARTIST_BIO_LISTENER_FACTORY)
    public void consume(ArtistBioMessage payload, Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        if (payload == null || payload.jobId() == null) {
            channel.basicReject(tag, false);
            return;
        }
        try {
            switch (processor.process(payload.jobId())) {
                case RETRY -> producer.retry(payload.jobId());
                case DEAD -> producer.dead(payload.jobId());
                default -> { }
            }
        } catch (RuntimeException exception) {
            log.warn("Artist bio delivery {} could not finish ({})", payload.jobId(), exception.getClass().getSimpleName());
            channel.basicNack(tag, false, true);
            return;
        }
        channel.basicAck(tag, false);
    }
}
