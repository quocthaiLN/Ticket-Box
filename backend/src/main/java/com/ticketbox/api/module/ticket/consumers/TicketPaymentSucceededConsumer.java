package com.ticketbox.api.module.ticket.consumers;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderItem;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import com.ticketbox.api.module.ticket.domain.entities.Ticket;
import com.ticketbox.api.module.ticket.events.TicketIssuedEvent;
import com.ticketbox.api.module.ticket.repositories.TicketRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class TicketPaymentSucceededConsumer {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @RabbitListener(queues = RabbitMqConstants.TICKET_PAYMENT_SUCCEEDED_QUEUE)
    public void issueTickets(PaymentCompletedEvent event) {
        Order order = orderRepository.findByIdForTicketIssuance(event.orderId())
                .orElseThrow(() -> new IllegalStateException("Paid order no longer exists"));
        if (ticketRepository.existsByOrderId(event.orderId())) {
            return;
        }
        if (order.getStatus() != OrderStatus.HELD) {
            throw new IllegalStateException("Paid order is not eligible for ticket issuance");
        }

        LocalDateTime issuedAt = LocalDateTime.now();
        List<Ticket> tickets = new ArrayList<>();
        for (OrderItem orderItem : order.getOrderItems()) {
            TicketType ticketType = orderItem.getTicketType();
            moveHeldInventoryToSold(ticketType, orderItem.getQuantity());
            for (int index = 0; index < orderItem.getQuantity(); index++) {
                tickets.add(Ticket.builder()
                        .order(order)
                        .orderItem(orderItem)
                        .user(order.getUser())
                        .concert(order.getConcert())
                        .ticketType(ticketType)
                        .seatZone(ticketType.getSeatZone())
                        .qrTokenHash(randomTokenHash())
                        .issuedAt(issuedAt)
                        .build());
            }
        }
        List<Ticket> savedTickets = ticketRepository.saveAll(tickets);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(issuedAt);
        eventPublisher.publishEvent(new TicketIssuedEvent(order.getId(), order.getUser().getId(),
                savedTickets.stream().map(Ticket::getId).toList()));
    }

    private void moveHeldInventoryToSold(TicketType ticketType, int quantity) {
        if (ticketType.getHeldQuantity() < quantity) {
            throw new IllegalStateException("Held inventory is inconsistent with paid order");
        }
        ticketType.setHeldQuantity(ticketType.getHeldQuantity() - quantity);
        ticketType.setSoldQuantity(ticketType.getSoldQuantity() + quantity);
    }

    private String randomTokenHash() {
        String randomToken = UUID.randomUUID() + ":" + UUID.randomUUID();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(randomToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
