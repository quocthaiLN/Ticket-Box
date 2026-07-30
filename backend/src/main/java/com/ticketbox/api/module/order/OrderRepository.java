package com.ticketbox.api.module.order;

import org.springframework.data.jpa.repository.JpaRepository;
import com.ticketbox.api.module.order.Order;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

}





