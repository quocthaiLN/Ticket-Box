package com.ticketbox.api.module.payment.repositories;

import com.ticketbox.api.module.payment.domain.entities.PaymentEvent;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

    Optional<PaymentEvent> findByProviderAndDedupeKey(PaymentProvider provider, String dedupeKey);

    boolean existsByProviderAndDedupeKey(PaymentProvider provider, String dedupeKey);

    List<PaymentEvent> findByPaymentIdOrderByReceivedAtAsc(UUID paymentId);
}
