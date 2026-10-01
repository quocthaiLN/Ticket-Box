package com.ticketbox.api.module.order.services;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface OrderInventoryService {

    void settlePaidOrder(UUID orderId, LocalDateTime settledAt);

    Optional<UUID> expireHeldOrder(UUID orderId, LocalDateTime expiredAt);
}
