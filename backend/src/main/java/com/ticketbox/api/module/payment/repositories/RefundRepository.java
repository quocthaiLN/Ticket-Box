package com.ticketbox.api.module.payment.repositories;

import com.ticketbox.api.module.payment.domain.entities.Refund;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<Refund> findByPaymentIdOrderByRequestedAtAsc(UUID paymentId);

    List<Refund> findByStatusIn(List<RefundStatus> statuses);

    @EntityGraph(attributePaths = {"payment"})
    @Query("SELECT r FROM Refund r WHERE r.id = :id")
    Optional<Refund> findWithPaymentById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"payment"})
    @Query("SELECT r FROM Refund r WHERE r.id = :id")
    Optional<Refund> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Refund r SET r.status = :newStatus WHERE r.id = :id AND r.status = :expectedStatus")
    int updateStatusIfExpected(
            @Param("id") UUID id,
            @Param("expectedStatus") RefundStatus expectedStatus,
            @Param("newStatus") RefundStatus newStatus);
}

