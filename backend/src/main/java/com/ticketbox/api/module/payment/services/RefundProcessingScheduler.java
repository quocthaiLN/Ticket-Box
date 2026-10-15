package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.payment.domain.dtos.RefundProcessMessage;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import com.ticketbox.api.module.payment.repositories.RefundRepository;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"api", "worker"})
public class RefundProcessingScheduler {

    private final RefundRepository refundRepository;
    private final RabbitTemplate rabbitTemplate;

    public RefundProcessingScheduler(RefundRepository refundRepository, RabbitTemplate rabbitTemplate) {
        this.refundRepository = refundRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void processPendingRefunds() {
        List<UUID> ids = refundRepository.findPendingRefundIds(
                List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING),
                PageRequest.of(0, 50));

        for (UUID id : ids) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMqConstants.PAYMENT_EXCHANGE,
                        RabbitMqConstants.PAYMENT_REFUND_PROCESS_ROUTING_KEY,
                        new RefundProcessMessage(id));
            } catch (RuntimeException exception) {
                log.error("Failed to publish refund processing check for refund {}", id, exception);
            }
        }
    }
}
