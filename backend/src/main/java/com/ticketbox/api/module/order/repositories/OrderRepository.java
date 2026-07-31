package com.ticketbox.api.module.order.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ticketbox.api.module.order.domain.entities.Order;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

}





