package com.ticketbox.api.module.order.services;

import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderItem;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounter;
import com.ticketbox.api.module.order.domain.exception.OrderNotFoundException;
import com.ticketbox.api.module.order.domain.exception.OrderNotSettlableException;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.order.repositories.UserTicketTypeCounterRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderInventoryServiceImpl implements OrderInventoryService {

    private final OrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserTicketTypeCounterRepository counterRepository;

    @Override
    @Transactional
    public void settlePaidOrder(UUID orderId, LocalDateTime settledAt) {
        Order order = orderRepository.findByIdForTicketIssuance(orderId)
                .orElseThrow(OrderNotFoundException::new);
        if (order.getStatus() != OrderStatus.HELD || order.getHoldExpiresAt() == null
                || !LocalDateTime.now().isBefore(order.getHoldExpiresAt())) {
            throw new OrderNotSettlableException();
        }

        for (OrderItem orderItem : order.getOrderItems()) {
            int quantity = orderItem.getQuantity();
            TicketType ticketType = ticketTypeRepository.findByIdForUpdate(orderItem.getTicketType().getId())
                    .orElseThrow(() -> new IllegalStateException("Ticket type for paid order no longer exists"));
            UserTicketTypeCounter counter = counterRepository
                    .findByUserIdAndTicketTypeIdForUpdate(order.getUser().getId(), ticketType.getId())
                    .orElseThrow(
                            () -> new IllegalStateException("User ticket counter for paid order no longer exists"));

            if (ticketType.getHeldQuantity() < quantity || counter.getHeldQuantity() < quantity) {
                throw new IllegalStateException("Held inventory is inconsistent with paid order");
            }
            ticketType.setHeldQuantity(ticketType.getHeldQuantity() - quantity);
            ticketType.setSoldQuantity(ticketType.getSoldQuantity() + quantity);
            counter.setHeldQuantity(counter.getHeldQuantity() - quantity);
            counter.setPaidQuantity(counter.getPaidQuantity() + quantity);
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(settledAt);
    }

    @Override
    @Transactional
    public Optional<UUID> expireHeldOrder(UUID orderId, LocalDateTime expiredAt) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.HELD || order.getHoldExpiresAt() == null
                || expiredAt.isBefore(order.getHoldExpiresAt())) {
            return Optional.empty();
        }

        for (OrderItem orderItem : order.getOrderItems().stream()
                .sorted(Comparator.comparing(item -> item.getTicketType().getId()))
                .toList()) {
            int quantity = orderItem.getQuantity();
            TicketType ticketType = ticketTypeRepository.findByIdForUpdate(orderItem.getTicketType().getId())
                    .orElseThrow(() -> new IllegalStateException("Ticket type for held order no longer exists"));
            UserTicketTypeCounter counter = counterRepository
                    .findByUserIdAndTicketTypeIdForUpdate(order.getUser().getId(), ticketType.getId())
                    .orElseThrow(() -> new IllegalStateException("User ticket counter for held order no longer exists"));

            if (ticketType.getHeldQuantity() < quantity || counter.getHeldQuantity() < quantity) {
                throw new IllegalStateException("Held inventory is inconsistent with expiring order");
            }
            ticketType.setHeldQuantity(ticketType.getHeldQuantity() - quantity);
            counter.setHeldQuantity(counter.getHeldQuantity() - quantity);
        }

        order.setStatus(OrderStatus.EXPIRED);
        order.setExpiredAt(expiredAt);
        return Optional.of(order.getConcert().getId());
    }
}
