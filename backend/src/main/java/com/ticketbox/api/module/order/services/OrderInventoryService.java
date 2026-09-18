package com.ticketbox.api.module.order.services;

import java.time.LocalDateTime;
import java.util.UUID;

public interface OrderInventoryService {

    void settlePaidOrder(UUID orderId, LocalDateTime settledAt);
}
