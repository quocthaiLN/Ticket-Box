package com.ticketbox.api.module.order.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderItem;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.domain.exception.OrderNotSettlableException;
import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounter;
import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounterId;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.order.repositories.UserTicketTypeCounterRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderInventoryServiceImplTest {

        @Mock
        private OrderRepository orderRepository;
        @Mock
        private TicketTypeRepository ticketTypeRepository;
        @Mock
        private UserTicketTypeCounterRepository counterRepository;
        @InjectMocks
        private OrderInventoryServiceImpl settlementService;

        @Test
        void settlesHeldInventoryAndUserQuotaInOneOperation() {
                UUID orderId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                UUID ticketTypeId = UUID.randomUUID();
                LocalDateTime settledAt = LocalDateTime.now();
                User user = User.builder().id(userId).build();
                TicketType ticketType = TicketType.builder()
                                .id(ticketTypeId)
                                .heldQuantity(3)
                                .soldQuantity(7)
                                .build();
                Order order = Order.builder()
                                .id(orderId)
                                .user(user)
                                .status(OrderStatus.HELD)
                                .holdExpiresAt(settledAt.plusMinutes(15))
                                .orderItems(List.of(OrderItem.builder().ticketType(ticketType).quantity(2).build()))
                                .build();
                UserTicketTypeCounter counter = UserTicketTypeCounter.builder()
                                .id(new UserTicketTypeCounterId(userId, ticketTypeId))
                                .heldQuantity(2)
                                .paidQuantity(1)
                                .build();

                when(orderRepository.findByIdForTicketIssuance(orderId)).thenReturn(Optional.of(order));
                when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(ticketType));
                when(counterRepository.findByUserIdAndTicketTypeIdForUpdate(userId, ticketTypeId))
                                .thenReturn(Optional.of(counter));

                settlementService.settlePaidOrder(orderId, settledAt);

                assertEquals(1, ticketType.getHeldQuantity());
                assertEquals(9, ticketType.getSoldQuantity());
                assertEquals(0, counter.getHeldQuantity());
                assertEquals(3, counter.getPaidQuantity());
                assertEquals(OrderStatus.CONFIRMED, order.getStatus());
                assertEquals(settledAt, order.getConfirmedAt());
        }

        @Test
        void expiresHeldOrderAndReleasesInventoryAndUserQuota() {
                UUID orderId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                UUID ticketTypeId = UUID.randomUUID();
                UUID concertId = UUID.randomUUID();
                LocalDateTime expiredAt = LocalDateTime.of(2026, 9, 18, 10, 15);
                TicketType ticketType = TicketType.builder().id(ticketTypeId)
                                .heldQuantity(3).soldQuantity(7).build();
                Order order = Order.builder().id(orderId)
                                .user(User.builder().id(userId).build())
                                .concert(Concert.builder().id(concertId).build())
                                .status(OrderStatus.HELD)
                                .holdExpiresAt(expiredAt)
                                .orderItems(List.of(OrderItem.builder().ticketType(ticketType).quantity(2).build()))
                                .build();
                UserTicketTypeCounter counter = UserTicketTypeCounter.builder()
                                .id(new UserTicketTypeCounterId(userId, ticketTypeId))
                                .heldQuantity(2).paidQuantity(1).build();

                when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
                when(ticketTypeRepository.findByIdForUpdate(ticketTypeId)).thenReturn(Optional.of(ticketType));
                when(counterRepository.findByUserIdAndTicketTypeIdForUpdate(userId, ticketTypeId))
                                .thenReturn(Optional.of(counter));

                assertEquals(Optional.of(concertId), settlementService.expireHeldOrder(orderId, expiredAt));
                assertEquals(1, ticketType.getHeldQuantity());
                assertEquals(7, ticketType.getSoldQuantity());
                assertEquals(0, counter.getHeldQuantity());
                assertEquals(1, counter.getPaidQuantity());
                assertEquals(OrderStatus.EXPIRED, order.getStatus());
                assertEquals(expiredAt, order.getExpiredAt());
        }

        @Test
        void doesNotExpireOrderBeforeDeadline() {
                UUID orderId = UUID.randomUUID();
                LocalDateTime now = LocalDateTime.of(2026, 9, 18, 10, 14);
                Order order = Order.builder().id(orderId).status(OrderStatus.HELD)
                                .holdExpiresAt(now.plusMinutes(1)).build();
                when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

                assertEquals(Optional.empty(), settlementService.expireHeldOrder(orderId, now));
                assertEquals(OrderStatus.HELD, order.getStatus());
                verifyNoInteractions(ticketTypeRepository, counterRepository);
        }

        @Test
        void doesNotReleaseInventoryForAlreadyConfirmedOrder() {
                UUID orderId = UUID.randomUUID();
                LocalDateTime now = LocalDateTime.of(2026, 9, 18, 10, 15);
                Order order = Order.builder().id(orderId).status(OrderStatus.CONFIRMED)
                                .holdExpiresAt(now.minusMinutes(1)).build();
                when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

                assertEquals(Optional.empty(), settlementService.expireHeldOrder(orderId, now));
                assertEquals(OrderStatus.CONFIRMED, order.getStatus());
                verifyNoInteractions(ticketTypeRepository, counterRepository);
        }

        @Test
        void rejectsPaymentSettlementAtHoldDeadline() {
                UUID orderId = UUID.randomUUID();
                LocalDateTime deadline = LocalDateTime.now().minusMinutes(1);
                Order order = Order.builder().id(orderId).status(OrderStatus.HELD)
                                .holdExpiresAt(deadline).build();
                when(orderRepository.findByIdForTicketIssuance(orderId)).thenReturn(Optional.of(order));

                OrderNotSettlableException exception = assertThrows(OrderNotSettlableException.class,
                                () -> settlementService.settlePaidOrder(orderId, deadline));

                assertEquals("ORDER_NOT_SETTLABLE", exception.getErrorCode().code());
                verifyNoInteractions(ticketTypeRepository, counterRepository);
        }
}
