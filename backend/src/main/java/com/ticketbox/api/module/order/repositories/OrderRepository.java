package com.ticketbox.api.module.order.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;
import com.ticketbox.api.module.order.domain.entities.Order;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = {"user", "concert", "orderItems", "orderItems.ticketType"})
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}





