package com.ticketbox.api.module.order.services;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderItemRequest;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderRequest;
import com.ticketbox.api.module.order.domain.dtos.HeldOrderItemResponse;
import com.ticketbox.api.module.order.domain.dtos.HeldOrderResponse;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderItem;
import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounter;
import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounterId;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.order.repositories.UserTicketTypeCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    public static final Duration HOLD_TTL = Duration.ofSeconds(900);

    private final ConcertRepository concertRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserTicketTypeCounterRepository counterRepository;
    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public HeldOrderResponse createHeldOrder(User currentUser, String idempotencyKey, CreateOrderRequest request) {
        List<CreateOrderItemRequest> sortedItems = validateAndSortItems(request);
        Concert concert = concertRepository.findById(request.getConcertId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CONCERT_NOT_FOUND", "Concert not found"));

        LocalDateTime now = LocalDateTime.now();
        List<LockedItem> lockedItems = sortedItems.stream()
                .map(item -> new LockedItem(item, lockAndValidateTicketType(item, concert, now)))
                .toList();

        String currency = lockedItems.getFirst().ticketType().getCurrency();
        if (lockedItems.stream().anyMatch(item -> !currency.equals(item.ticketType().getCurrency()))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_CHECKOUT_REQUEST",
                    "All ticket types in an order must use the same currency");
        }

        for (LockedItem item : lockedItems) {
            reserveInventoryAndQuota(currentUser, item.ticketType(), item.request().getQuantity());
        }

        LocalDateTime holdExpiresAt = now.plus(HOLD_TTL);
        Order order = Order.builder()
                .user(currentUser)
                .concert(concert)
                .idempotencyKey(idempotencyKey)
                .status(Order.OrderStatus.HELD)
                .currency(currency)
                .holdExpiresAt(holdExpiresAt)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (LockedItem item : lockedItems) {
            BigDecimal lineTotal = item.ticketType().getPrice().multiply(BigDecimal.valueOf(item.request().getQuantity()));
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .ticketType(item.ticketType())
                    .quantity(item.request().getQuantity())
                    .unitPrice(item.ticketType().getPrice())
                    .lineTotal(lineTotal)
                    .build();
            order.getOrderItems().add(orderItem);
            totalAmount = totalAmount.add(lineTotal);
        }
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);
        return mapToHeldOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<HeldOrderResponse> findExistingOrder(User currentUser, String idempotencyKey,
                                                                    CreateOrderRequest request) {
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(order -> {
                    if (!matchesExistingOrder(order, currentUser, request)) {
                        throw new AppException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REUSED",
                                "Idempotency key was already used for a different request");
                    }
                    return mapToHeldOrderResponse(order);
                });
    }

    private List<CreateOrderItemRequest> validateAndSortItems(CreateOrderRequest request) {
        Set<UUID> ticketTypeIds = new HashSet<>();
        for (CreateOrderItemRequest item : request.getItems()) {
            if (!ticketTypeIds.add(item.getTicketTypeId())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_CHECKOUT_REQUEST",
                        "Each ticket type may appear only once in an order");
            }
        }
        return request.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getTicketTypeId().toString()))
                .toList();
    }

    private boolean matchesExistingOrder(Order order, User currentUser, CreateOrderRequest request) {
        if (!order.getUser().getId().equals(currentUser.getId())
                || !order.getConcert().getId().equals(request.getConcertId())
                || order.getOrderItems().size() != request.getItems().size()) {
            return false;
        }

        return order.getOrderItems().stream()
                .allMatch(orderItem -> request.getItems().stream().anyMatch(requestItem ->
                        requestItem.getTicketTypeId().equals(orderItem.getTicketType().getId())
                                && requestItem.getQuantity().equals(orderItem.getQuantity())));
    }

    private TicketType lockAndValidateTicketType(CreateOrderItemRequest request, Concert concert, LocalDateTime now) {
        TicketType ticketType = ticketTypeRepository.findByIdForUpdate(request.getTicketTypeId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found"));

        if (!ticketType.getConcert().getId().equals(concert.getId())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_CHECKOUT_REQUEST",
                    "All ticket types must belong to the requested concert");
        }
        if (ticketType.getStatus() != TicketType.TicketTypeStatus.ON_SALE) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "TICKET_TYPE_NOT_ON_SALE",
                    "Ticket type is not on sale");
        }
        if (now.isBefore(ticketType.getSaleStartAt()) || !now.isBefore(ticketType.getSaleEndAt())) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "SALE_WINDOW_CLOSED",
                    "Ticket type is outside its sale window");
        }
        if (ticketType.getAvailableQuantity() < request.getQuantity()) {
            throw new AppException(HttpStatus.CONFLICT, "TICKET_SOLD_OUT", "Insufficient ticket inventory");
        }
        return ticketType;
    }

    private void reserveInventoryAndQuota(User user, TicketType ticketType, int requestedQuantity) {
        UserTicketTypeCounter counter = counterRepository
                .findByUserIdAndTicketTypeIdForUpdate(user.getId(), ticketType.getId())
                .orElseGet(() -> UserTicketTypeCounter.builder()
                        .id(new UserTicketTypeCounterId(user.getId(), ticketType.getId()))
                        .user(user)
                        .ticketType(ticketType)
                        .heldQuantity(0)
                        .paidQuantity(0)
                        .build());

        if (counter.getHeldQuantity() + counter.getPaidQuantity() + requestedQuantity > ticketType.getMaxPerUser()) {
            throw new AppException(HttpStatus.CONFLICT, "PER_USER_LIMIT_EXCEEDED",
                    "Ticket purchase limit per user would be exceeded");
        }

        ticketType.setHeldQuantity(ticketType.getHeldQuantity() + requestedQuantity);
        counter.setHeldQuantity(counter.getHeldQuantity() + requestedQuantity);
        counterRepository.save(counter);
    }

    private HeldOrderResponse mapToHeldOrderResponse(Order order) {
        List<HeldOrderItemResponse> items = order.getOrderItems().stream()
                .map(item -> HeldOrderItemResponse.builder()
                        .ticketTypeId(item.getTicketType().getId())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();

        return HeldOrderResponse.builder()
                .orderId(order.getId())
                .concertId(order.getConcert().getId())
                .status(order.getStatus().name())
                .items(items)
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .holdExpiresAt(order.getHoldExpiresAt())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private record LockedItem(CreateOrderItemRequest request, TicketType ticketType) {
    }
}
