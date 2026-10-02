package com.ticketbox.api.module.order.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.catalog.domain.entities.TicketTypeStatus;
import com.ticketbox.api.module.catalog.domain.exception.ConcertNotFoundException;
import com.ticketbox.api.module.catalog.domain.exception.TicketTypeNotFoundException;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderItemRequest;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderRequest;
import com.ticketbox.api.module.order.domain.dtos.OrderItemResponse;
import com.ticketbox.api.module.order.domain.dtos.OrderResponse;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderItem;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounter;
import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounterId;
import com.ticketbox.api.module.order.domain.exception.OrderAccessDeniedException;
import com.ticketbox.api.module.order.domain.exception.OrderNotFoundException;
import com.ticketbox.api.module.order.domain.exception.PerUserLimitExceededException;
import com.ticketbox.api.module.order.domain.exception.SaleWindowClosedException;
import com.ticketbox.api.module.order.domain.exception.TicketSoldOutException;
import com.ticketbox.api.module.order.domain.exception.TicketTypeNotOnSaleException;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.order.repositories.UserTicketTypeCounterRepository;
import com.ticketbox.api.module.shared.cache.CacheService;
import com.ticketbox.api.module.shared.idempotency.IdempotencyService;
import com.ticketbox.api.module.shared.idempotency.exception.IdempotencyKeyReusedException;
import com.ticketbox.api.module.shared.validation.RequestValidationException;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    @Value("${app.hold-duration.hold-minutes:15}")
    private long holdMinutes = 15;

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:hold:";

    private final ConcertRepository concertRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserTicketTypeCounterRepository counterRepository;
    private final OrderRepository orderRepository;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;
    private final CacheService cacheService;

    public Duration getHoldTtl() {
        return Duration.ofMinutes(holdMinutes);
    }

    @Override
    public OrderResponse createHeldOrder(User currentUser, String idempotencyKey, CreateOrderRequest request) {
        String fingerprint = fingerprint(currentUser, request);
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        return idempotencyService.execute(
                redisKey,
                fingerprint,
                getHoldTtl(),
                OrderResponse.class,
                () -> findExistingOrder(currentUser, idempotencyKey, request),
                () -> {
                    OrderResponse heldOrder = transactionTemplate
                            .execute(status -> executeCreateHeldOrder(currentUser, idempotencyKey, request));
                    cacheService.evictConcertCache(request.getConcertId());
                    return heldOrder;
                });
    }

    private OrderResponse executeCreateHeldOrder(User currentUser, String idempotencyKey,
            CreateOrderRequest request) {
        List<CreateOrderItemRequest> sortedItems = validateAndSortItems(request);
        Concert concert = concertRepository.findById(request.getConcertId())
                .orElseThrow(() -> new ConcertNotFoundException(request.getConcertId()));

        LocalDateTime now = LocalDateTime.now();
        List<LockedItem> lockedItems = sortedItems.stream()
                .map(item -> new LockedItem(item, lockAndValidateTicketType(item, concert, now)))
                .toList();

        String currency = lockedItems.getFirst().ticketType().getCurrency();
        if (lockedItems.stream().anyMatch(item -> !currency.equals(item.ticketType().getCurrency()))) {
            throw new RequestValidationException("INVALID_CHECKOUT_REQUEST",
                    "All ticket types in an order must use the same currency");
        }

        for (LockedItem item : lockedItems) {
            reserveInventoryAndQuota(currentUser, item.ticketType(), item.request().getQuantity());
        }

        LocalDateTime holdExpiresAt = now.plus(getHoldTtl());
        Order order = Order.builder()
                .user(currentUser)
                .concert(concert)
                .idempotencyKey(idempotencyKey)
                .status(OrderStatus.HELD)
                .currency(currency)
                .holdExpiresAt(holdExpiresAt)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (LockedItem item : lockedItems) {
            BigDecimal lineTotal = item.ticketType().getPrice()
                    .multiply(BigDecimal.valueOf(item.request().getQuantity()));
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
        return mapToOrderResponse(savedOrder);
    }

    @Override
    public OrderResponse getOrder(User currentUser, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(OrderNotFoundException::new);
        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new OrderAccessDeniedException();
        }
        return mapToOrderResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<OrderResponse> findExistingOrder(User currentUser, String idempotencyKey,
            CreateOrderRequest request) {
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(order -> {
                    if (!matchesExistingOrder(order, currentUser, request)) {
                        throw new IdempotencyKeyReusedException();
                    }
                    return mapToOrderResponse(order);
                });
    }

    private List<CreateOrderItemRequest> validateAndSortItems(CreateOrderRequest request) {
        Set<UUID> ticketTypeIds = new HashSet<>();
        for (CreateOrderItemRequest item : request.getItems()) {
            if (!ticketTypeIds.add(item.getTicketTypeId())) {
                throw new RequestValidationException("INVALID_CHECKOUT_REQUEST",
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
                .allMatch(orderItem -> request.getItems().stream()
                        .anyMatch(requestItem -> requestItem.getTicketTypeId().equals(orderItem.getTicketType().getId())
                                && requestItem.getQuantity().equals(orderItem.getQuantity())));
    }

    private TicketType lockAndValidateTicketType(CreateOrderItemRequest request, Concert concert, LocalDateTime now) {
        TicketType ticketType = ticketTypeRepository.findByIdForUpdate(request.getTicketTypeId())
                .orElseThrow(() -> new TicketTypeNotFoundException(request.getTicketTypeId()));

        if (!ticketType.getConcert().getId().equals(concert.getId())) {
            throw new RequestValidationException("INVALID_CHECKOUT_REQUEST",
                    "All ticket types must belong to the requested concert");
        }
        if (ticketType.getStatus() != TicketTypeStatus.ON_SALE) {
            throw new TicketTypeNotOnSaleException();
        }
        if (now.isBefore(ticketType.getSaleStartAt()) || !now.isBefore(ticketType.getSaleEndAt())) {
            throw new SaleWindowClosedException();
        }
        if (ticketType.getAvailableQuantity() < request.getQuantity()) {
            throw new TicketSoldOutException();
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
            throw new PerUserLimitExceededException();
        }

        ticketType.setHeldQuantity(ticketType.getHeldQuantity() + requestedQuantity);
        counter.setHeldQuantity(counter.getHeldQuantity() + requestedQuantity);
        counterRepository.save(counter);
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .ticketTypeId(item.getTicketType().getId())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();

        return OrderResponse.builder()
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

    private String fingerprint(User currentUser, CreateOrderRequest request) {
        String payload = currentUser.getId() + "|" + request.getConcertId() + "|"
                + request.getItems().stream()
                        .sorted(Comparator.comparing(item -> item.getTicketTypeId().toString()))
                        .map(item -> item.getTicketTypeId() + ":" + item.getQuantity())
                        .reduce("", (left, right) -> left + "|" + right);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private record LockedItem(CreateOrderItemRequest request, TicketType ticketType) {
    }
}
