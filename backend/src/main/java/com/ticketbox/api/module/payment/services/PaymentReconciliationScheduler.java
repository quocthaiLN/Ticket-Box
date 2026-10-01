package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.repositories.PaymentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {
    private final PaymentRepository paymentRepository;
    private final PaymentServiceImpl paymentService;

    @Scheduled(fixedDelay = 30_000)
    public void reconcileDuePayments() {
        List<UUID> ids = paymentRepository.findDueIds(LocalDateTime.now(),
                List.of(PaymentStatus.CREATING, PaymentStatus.PENDING), PageRequest.of(0, 50));
        for (UUID id : ids) {
            try {
                paymentService.reconcilePayment(id);
            } catch (RuntimeException exception) {
                log.error("Failed to reconcile payment {}", id, exception);
            }
        }
    }
}
