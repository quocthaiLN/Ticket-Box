package com.ticketbox.api.module.notification.consumer;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import com.ticketbox.api.module.notification.services.EmailService;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import com.ticketbox.api.module.ticket.events.TicketIssuedEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)

class NotificationConsumerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "CANCELLED"})
    void sendsPaymentFailedEmailToPaymentOwner(PaymentStatus status) {
        UUID userId = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(UUID.randomUUID(), UUID.randomUUID(), userId,
                PaymentProvider.MOMO, status, BigDecimal.valueOf(100_000), "VND", "provider-txn");
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().email("buyer@example.com").build()));

        notificationConsumer.receivePaymentFailed(event);

        verify(emailService).sendPaymentFailedEmail("buyer@example.com", event);
    }

    @Test
    void sendsTicketIssuedEmailToTicketOwner() {
        UUID userId = UUID.randomUUID();
        TicketIssuedEvent event = new TicketIssuedEvent(UUID.randomUUID(), userId,
                List.of(UUID.randomUUID(), UUID.randomUUID()));
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().email("buyer@example.com").build()));

        notificationConsumer.receiveTicketIssued(event);

        verify(emailService).sendTicketIssuedEmail("buyer@example.com", event);
    }
}
