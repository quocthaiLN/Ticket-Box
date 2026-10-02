package com.ticketbox.api.module.order.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.catalog.domain.entities.TicketTypeStatus;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderItemRequest;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderRequest;
import com.ticketbox.api.module.order.domain.dtos.OrderResponse;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.exception.PerUserLimitExceededException;
import com.ticketbox.api.module.order.domain.exception.TicketSoldOutException;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.order.repositories.UserTicketTypeCounterRepository;
import com.ticketbox.api.module.shared.cache.CacheService;
import com.ticketbox.api.module.shared.validation.RequestValidationException;
import com.ticketbox.api.module.shared.idempotency.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private UserTicketTypeCounterRepository counterRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User user;
    private Concert concert;
    private TicketType firstTicketType;
    private TicketType secondTicketType;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.fromString("44444444-4444-4444-8444-444444444444"))
                .email("audience@example.com")
                .fullName("Audience")
                .role(UserRole.AUDIENCE)
                .build();
        concert = Concert.builder()
                .id(UUID.fromString("11111111-1111-1111-8111-111111111111"))
                .title("Concert")
                .slug("concert")
                .venue("Venue")
                .artistName("Artist")
                .startsAt(LocalDateTime.now().plusDays(1))
                .endsAt(LocalDateTime.now().plusDays(1).plusHours(2))
                .organizer(user)
                .build();
        firstTicketType = ticketType("00000000-0000-4000-8000-000000000001", 5, 4, new BigDecimal("100000"));
        secondTicketType = ticketType("ffffffff-ffff-4fff-8fff-ffffffffffff", 10, 4, new BigDecimal("200000"));

        lenient().when(idempotencyService.execute(anyString(), anyString(), any(Duration.class), any(Class.class), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> action = invocation.getArgument(5);
                    return action.get();
                });
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    @DisplayName("Creates a held order after locking ticket types in UUID order")
    void createHeldOrder_sortsLocksAndReservesInventoryAndQuota() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .concertId(concert.getId())
                .items(List.of(item(secondTicketType.getId(), 2), item(firstTicketType.getId(), 1)))
                .build();
        Map<UUID, TicketType> ticketTypes = Map.of(
                firstTicketType.getId(), firstTicketType,
                secondTicketType.getId(), secondTicketType);

        when(concertRepository.findById(concert.getId())).thenReturn(Optional.of(concert));
        when(ticketTypeRepository.findByIdForUpdate(any(UUID.class)))
                .thenAnswer(invocation -> Optional.of(ticketTypes.get(invocation.getArgument(0))));
        when(counterRepository.findByUserIdAndTicketTypeIdForUpdate(eq(user.getId()), any(UUID.class))).thenReturn(Optional.empty());
        when(counterRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(UUID.fromString("99999999-9999-4999-8999-999999999999"));
            order.setCreatedAt(LocalDateTime.now());
            return order;
        });

        OrderResponse response = orderService.createHeldOrder(user, "c41e9a00-1111-4111-8111-111111111111", request);

        assertEquals("HELD", response.getStatus());
        assertEquals(new BigDecimal("500000"), response.getTotalAmount());
        assertEquals(1, firstTicketType.getHeldQuantity());
        assertEquals(2, secondTicketType.getHeldQuantity());
        assertEquals(2, response.getItems().size());
        InOrder locks = inOrder(ticketTypeRepository);
        locks.verify(ticketTypeRepository).findByIdForUpdate(firstTicketType.getId());
        locks.verify(ticketTypeRepository).findByIdForUpdate(secondTicketType.getId());
        verify(counterRepository, times(2)).save(any());
        verify(orderRepository).save(any(Order.class));
        verify(idempotencyService).execute(eq("idempotency:hold:c41e9a00-1111-4111-8111-111111111111"), anyString(),
                eq(orderService.getHoldTtl()), eq(OrderResponse.class), any(), any());
        verify(transactionTemplate).execute(any());
        verify(cacheService).evictConcertCache(concert.getId());
    }

    @Test
    @DisplayName("Does not evict inventory cache when idempotency replays an existing order")
    void createHeldOrder_doesNotEvictCacheWhenIdempotencyReplaysOrder() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .concertId(concert.getId())
                .items(List.of(item(firstTicketType.getId(), 1)))
                .build();
        OrderResponse replayedResponse = OrderResponse.builder()
                .orderId(UUID.randomUUID())
                .concertId(concert.getId())
                .status("HELD")
                .build();

        when(idempotencyService.execute(anyString(), anyString(), any(Duration.class), eq(OrderResponse.class), any(), any()))
                .thenReturn(replayedResponse);

        OrderResponse response = orderService.createHeldOrder(user, "c41e9a00-1111-4111-8111-111111111111", request);

        assertEquals(replayedResponse, response);
        verifyNoInteractions(cacheService);
        verifyNoInteractions(orderRepository);
    }

    @Test
    @DisplayName("Rejects repeated ticket types before taking any database lock")
    void createHeldOrder_rejectsDuplicateTicketTypes() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .concertId(concert.getId())
                .items(List.of(item(firstTicketType.getId(), 1), item(firstTicketType.getId(), 2)))
                .build();

        RequestValidationException exception = assertThrows(RequestValidationException.class,
                () -> orderService.createHeldOrder(user, "c41e9a00-1111-4111-8111-111111111111", request));

        assertEquals("INVALID_CHECKOUT_REQUEST", exception.getCode());
        verify(ticketTypeRepository, never()).findByIdForUpdate(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects a request that would exceed the per-user ticket limit")
    void createHeldOrder_rejectsPerUserLimit() {
        firstTicketType.setMaxPerUser(2);
        CreateOrderRequest request = CreateOrderRequest.builder()
                .concertId(concert.getId())
                .items(List.of(item(firstTicketType.getId(), 3)))
                .build();

        when(concertRepository.findById(concert.getId())).thenReturn(Optional.of(concert));
        when(ticketTypeRepository.findByIdForUpdate(firstTicketType.getId())).thenReturn(Optional.of(firstTicketType));
        when(counterRepository.findByUserIdAndTicketTypeIdForUpdate(user.getId(), firstTicketType.getId())).thenReturn(Optional.empty());

        PerUserLimitExceededException exception = assertThrows(PerUserLimitExceededException.class,
                () -> orderService.createHeldOrder(user, "c41e9a00-1111-4111-8111-111111111111", request));

        assertEquals("PER_USER_LIMIT_EXCEEDED", exception.getErrorCode().code());
        assertEquals(0, firstTicketType.getHeldQuantity());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects an order that exceeds the locked ticket inventory")
    void createHeldOrder_rejectsSoldOutTicketType() {
        firstTicketType.setTotalQuantity(1);
        CreateOrderRequest request = CreateOrderRequest.builder()
                .concertId(concert.getId())
                .items(List.of(item(firstTicketType.getId(), 2)))
                .build();

        when(concertRepository.findById(concert.getId())).thenReturn(Optional.of(concert));
        when(ticketTypeRepository.findByIdForUpdate(firstTicketType.getId())).thenReturn(Optional.of(firstTicketType));

        TicketSoldOutException exception = assertThrows(TicketSoldOutException.class,
                () -> orderService.createHeldOrder(user, "c41e9a00-1111-4111-8111-111111111111", request));

        assertEquals("TICKET_SOLD_OUT", exception.getErrorCode().code());
        assertEquals(0, firstTicketType.getHeldQuantity());
        verify(orderRepository, never()).save(any());
    }

    private TicketType ticketType(String id, int totalQuantity, int maxPerUser, BigDecimal price) {
        return TicketType.builder()
                .id(UUID.fromString(id))
                .concert(concert)
                .name("Ticket " + id)
                .price(price)
                .currency("VND")
                .totalQuantity(totalQuantity)
                .heldQuantity(0)
                .soldQuantity(0)
                .maxPerUser(maxPerUser)
                .saleStartAt(LocalDateTime.now().minusHours(1))
                .saleEndAt(LocalDateTime.now().plusHours(1))
                .status(TicketTypeStatus.ON_SALE)
                .build();
    }

    private CreateOrderItemRequest item(UUID ticketTypeId, int quantity) {
        return CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId)
                .quantity(quantity)
                .build();
    }
}
