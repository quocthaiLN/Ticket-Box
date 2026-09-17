package com.ticketbox.api.module.order.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import com.ticketbox.api.module.order.domain.entities.Order;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = {"user", "concert", "orderItems", "orderItems.ticketType"})
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    @EntityGraph(attributePaths = {"user", "concert"})
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o JOIN FETCH o.orderItems oi JOIN FETCH oi.ticketType t LEFT JOIN FETCH t.seatZone WHERE o.id = :id")
    @EntityGraph(attributePaths = {"user", "concert", "orderItems", "orderItems.ticketType",
            "orderItems.ticketType.seatZone"})
    Optional<Order> findByIdForTicketIssuance(@Param("id") UUID id);

}
