package com.ticketbox.api.module.payment.repositories;

import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"order", "order.user"})
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"order", "order.user"})
    Optional<Payment> findPaymentWithOrderAndUserById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    @EntityGraph(attributePaths = {"order", "order.user"})
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    @Query("SELECT p.id FROM Payment p WHERE p.nextReconcileAt <= :now "
            + "AND p.status IN :statuses ORDER BY p.nextReconcileAt")
    List<UUID> findDueIds(@Param("now") LocalDateTime now,
            @Param("statuses") List<PaymentStatus> statuses, Pageable pageable);
}
