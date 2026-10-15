package com.ticketbox.api.module.payment.repositories;

import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @EntityGraph(attributePaths = {"order", "order.user"})
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"order", "order.user"})
    Optional<Payment> findByMerchantTxnRef(String merchantTxnRef);

    @EntityGraph(attributePaths = {"order", "order.user"})
    Optional<Payment> findPaymentWithOrderAndUserById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    @EntityGraph(attributePaths = {"order", "order.user"})
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.merchantTxnRef = :merchantTxnRef")
    @EntityGraph(attributePaths = {"order", "order.user"})
    Optional<Payment> findByMerchantTxnRefForUpdate(@Param("merchantTxnRef") String merchantTxnRef);

    boolean existsByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    @Query("SELECT p.id FROM Payment p WHERE p.nextReconcileAt <= :now "
            + "AND p.status IN :statuses ORDER BY p.nextReconcileAt")
    List<UUID> findDueIds(@Param("now") LocalDateTime now,
            @Param("statuses") List<PaymentStatus> statuses, Pageable pageable);
}
