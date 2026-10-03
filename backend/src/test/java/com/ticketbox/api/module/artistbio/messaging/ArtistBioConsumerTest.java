package com.ticketbox.api.module.artistbio.messaging;

import com.rabbitmq.client.Channel;
import com.ticketbox.api.module.artistbio.services.ArtistBioProcessor;
import com.ticketbox.api.module.artistbio.services.ArtistBioJobStateService.Outcome;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.core.*;
import static org.mockito.Mockito.*;

class ArtistBioConsumerTest {
    private final ArtistBioProcessor processor = mock(ArtistBioProcessor.class);
    private final ArtistBioProducer producer = mock(ArtistBioProducer.class);
    private final Channel channel = mock(Channel.class);
    private final ArtistBioConsumer consumer = new ArtistBioConsumer(processor, producer);
    private final UUID id = UUID.randomUUID();
    private Message message() {
        var properties = new MessageProperties(); properties.setDeliveryTag(42);
        return new Message(new byte[0], properties);
    }
    @ParameterizedTest @EnumSource(value = Outcome.class, names = {"DONE", "SKIP", "RETRY", "DEAD"})
    void acknowledgesOnlyAfterProcessingAndConfirmedForward(Outcome outcome) throws Exception {
        when(processor.process(id)).thenReturn(outcome);
        consumer.consume(new ArtistBioMessage(id), message(), channel);
        var order = inOrder(processor, producer, channel);
        order.verify(processor).process(id);
        if (outcome == Outcome.RETRY) order.verify(producer).retry(id);
        if (outcome == Outcome.DEAD) order.verify(producer).dead(id);
        order.verify(channel).basicAck(42, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }
    @ParameterizedTest @EnumSource(value = Outcome.class, names = {"RETRY", "DEAD"})
    void publishFailureDoesNotAck(Outcome outcome) throws Exception {
        when(processor.process(id)).thenReturn(outcome);
        if (outcome == Outcome.RETRY) doThrow(new IllegalStateException()).when(producer).retry(id);
        else doThrow(new IllegalStateException()).when(producer).dead(id);
        consumer.consume(new ArtistBioMessage(id), message(), channel);
        verify(channel).basicNack(42, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
    @Test void databaseFailureDoesNotAck() throws Exception {
        when(processor.process(id)).thenThrow(new IllegalStateException());
        consumer.consume(new ArtistBioMessage(id), message(), channel);
        verify(channel).basicNack(42, false, true);
        verifyNoInteractions(producer);
    }
    @Test void malformedMessageIsRejected() throws Exception {
        consumer.consume(new ArtistBioMessage(null), message(), channel);
        verify(channel).basicReject(42, false);
        verifyNoInteractions(processor, producer);
    }
}
