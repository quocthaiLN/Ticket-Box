package com.ticketbox.api.module.ticket.consumers;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderItem;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import com.ticketbox.api.module.ticket.domain.entities.Ticket;
import com.ticketbox.api.module.ticket.events.TicketIssuedEvent;
import com.ticketbox.api.module.ticket.repositories.TicketRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.ticketbox.api.module.ticket.services.TicketQrService;

@Component
@RequiredArgsConstructor
public class TicketPaymentSucceededConsumer {

    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TicketQrService ticketQrService;

    @Transactional
    @RabbitListener(queues = RabbitMqConstants.TICKET_PAYMENT_SUCCEEDED_QUEUE)
    public void issueTickets(PaymentCompletedEvent event) {
        Order order = orderRepository.findByIdForTicketIssuance(event.orderId())
                .orElseThrow(() -> new IllegalStateException("Paid order no longer exists"));
        if (ticketRepository.existsByOrderId(event.orderId())) {
            return;
        }
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Paid order is not eligible for ticket issuance");
        }

        LocalDateTime issuedAt = LocalDateTime.now();
        List<Ticket> tickets = new ArrayList<>();
        for (OrderItem orderItem : order.getOrderItems()) {
            var ticketType = orderItem.getTicketType();
            for (int index = 0; index < orderItem.getQuantity(); index++) {
                tickets.add(Ticket.builder()
                        .order(order)
                        .orderItem(orderItem)
                        .user(order.getUser())
                        .concert(order.getConcert())
                        .ticketType(ticketType)
                        .seatZone(ticketType.getSeatZone())
                        .qrTokenHash(ticketQrService.createPendingTokenHash())
                        .issuedAt(issuedAt)
                        .build());
            }
        }
        List<Ticket> savedTickets = ticketRepository.saveAllAndFlush(tickets);
        for (Ticket ticket : savedTickets) {
            TicketQrService.IssuedQr qr = ticketQrService.issue(ticket.getId());
            ticket.setQrTokenHash(qr.tokenHash());
            ticket.setQrPayload(qr.payload());
            ticket.setQrSignature(qr.signature());
        }
        eventPublisher.publishEvent(new TicketIssuedEvent(order.getId(), order.getUser().getId(),
                savedTickets.stream().map(Ticket::getId).toList()));
    }

}
