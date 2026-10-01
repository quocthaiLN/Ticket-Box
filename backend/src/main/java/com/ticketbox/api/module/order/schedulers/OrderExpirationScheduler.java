package com.ticketbox.api.module.order.schedulers;

import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.order.services.OrderInventoryService;
import com.ticketbox.api.module.shared.cache.CacheService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderRepository orderRepository;
    private final OrderInventoryService orderInventoryService;
    private final CacheService cacheService;

    @Scheduled(fixedDelay = 30000)
    public void expireOrders() {
        LocalDateTime now = LocalDateTime.now();
        for (UUID orderId : orderRepository.findExpiredHeldOrderIds(OrderStatus.HELD, now)) {
            try {
                orderInventoryService.expireHeldOrder(orderId, now)
                        .ifPresent(cacheService::evictConcertCache);
            } catch (Exception exception) {
                log.error("Failed to expire order {}", orderId, exception);
            }
        }
    }
}
