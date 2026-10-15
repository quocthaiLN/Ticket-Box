package com.ticketbox.api.module.payment.repositories;

import com.ticketbox.api.module.payment.domain.entities.Refund;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<Refund> findByPaymentIdOrderByRequestedAtAsc(UUID paymentId);

    List<Refund> findByStatusIn(List<RefundStatus> statuses);
}
