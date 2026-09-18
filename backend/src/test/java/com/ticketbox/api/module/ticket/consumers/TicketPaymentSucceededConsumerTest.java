package com.ticketbox.api.module.ticket.consumers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.SeatZone;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderItem;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import com.ticketbox.api.module.ticket.domain.entities.Ticket;
import com.ticketbox.api.module.ticket.events.TicketIssuedEvent;
import com.ticketbox.api.module.ticket.repositories.TicketRepository;
import com.ticketbox.api.module.ticket.services.TicketQrService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TicketPaymentSucceededConsumerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TicketQrService ticketQrService;
    @InjectMocks
    private TicketPaymentSucceededConsumer consumer;

    @Test
    void persistsNewTicketsBeforeCreatingTheirQrCodes() {
        UUID orderId = UUID.randomUUID();
        UUID generatedTicketId = UUID.randomUUID();
        Order order = confirmedOrder(orderId);
        PaymentCompletedEvent paymentEvent = new PaymentCompletedEvent(UUID.randomUUID(), orderId,
                order.getUser().getId(), PaymentProvider.MOMO, PaymentStatus.SUCCEEDED,
                BigDecimal.valueOf(100_000), "VND", "provider-transaction");
        AtomicBoolean persistedWithNullId = new AtomicBoolean(false);

        when(orderRepository.findByIdForTicketIssuance(orderId)).thenReturn(Optional.of(order));
        when(ticketRepository.existsByOrderId(orderId)).thenReturn(false);
        when(ticketQrService.createPendingTokenHash()).thenReturn("pending-token-hash");
        when(ticketRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<Ticket> persistedTickets = List.copyOf(invocation.getArgument(0));
            persistedWithNullId.set(persistedTickets.stream().allMatch(ticket -> ticket.getId() == null));
            persistedTickets.getFirst().setId(generatedTicketId);
            return persistedTickets;
        });
        when(ticketQrService.issue(generatedTicketId)).thenReturn(new TicketQrService.IssuedQr(
                "{\"ticket_id\":\"" + generatedTicketId + "\"}", "signature", "final-token-hash"));

        consumer.issueTickets(paymentEvent);

        assertTrue(persistedWithNullId.get());
        ArgumentCaptor<TicketIssuedEvent> eventCaptor = ArgumentCaptor.forClass(TicketIssuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertTrue(eventCaptor.getValue().ticketIds().contains(generatedTicketId));
    }

    private Order confirmedOrder(UUID orderId) {
        User user = User.builder().id(UUID.randomUUID()).build();
        Concert concert = Concert.builder().id(UUID.randomUUID()).build();
        SeatZone seatZone = SeatZone.builder().id(UUID.randomUUID()).build();
        TicketType ticketType = TicketType.builder().id(UUID.randomUUID()).seatZone(seatZone).build();
        Order order = Order.builder()
                .id(orderId)
                .user(user)
                .concert(concert)
                .status(OrderStatus.CONFIRMED)
                .build();
        OrderItem item = OrderItem.builder().order(order).ticketType(ticketType).quantity(1).build();
        order.setOrderItems(List.of(item));
        return order;
    }
}
