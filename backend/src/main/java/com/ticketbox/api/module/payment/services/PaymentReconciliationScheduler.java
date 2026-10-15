package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.payment.domain.dtos.PaymentReconcileMessage;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.repositories.PaymentRepository;
import java.time.LocalDateTime;
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
public class PaymentReconciliationScheduler {

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;

    public PaymentReconciliationScheduler(PaymentRepository paymentRepository, RabbitTemplate rabbitTemplate) {
        this.paymentRepository = paymentRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 30_000)
    public void reconcileDuePayments() {
        List<UUID> ids = paymentRepository.findDueIds(
                LocalDateTime.now(),
                List.of(PaymentStatus.INITIATED, PaymentStatus.CREATING, PaymentStatus.PENDING),
                PageRequest.of(0, 50));

        for (UUID id : ids) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMqConstants.PAYMENT_EXCHANGE,
                        RabbitMqConstants.PAYMENT_RECONCILE_ROUTING_KEY,
                        new PaymentReconcileMessage(id));
            } catch (RuntimeException exception) {
                log.error("Failed to publish reconciliation check for payment {}", id, exception);
            }
        }
    }
}
