package com.ticketbox.api.module.notification.consumer;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import com.ticketbox.api.module.notification.services.EmailService;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import com.ticketbox.api.module.shared.domain.dtos.AuthOtpMessageDTO;
import com.ticketbox.api.module.ticket.events.TicketIssuedEvent;

import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationConsumer {

    private final EmailService emailService;
    private final UserRepository userRepository;

    public NotificationConsumer(EmailService emailService, UserRepository userRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
    }

    @RabbitListener(queues = RabbitMqConstants.AUTH_OTP_QUEUE)
    public void receiveOtpMessage(AuthOtpMessageDTO message) {
        log.info("Received OTP message from queue [{}] for email: {}", RabbitMqConstants.AUTH_OTP_QUEUE, message.getEmail());
        try {
            emailService.sendOtpEmail(message);
            log.info("Finished processing OTP message for email: {}", message.getEmail());
        } catch (Exception e) {
            log.error("Error processing OTP message for email: {}", message.getEmail(), e);
            // Exception will trigger RabbitMQ listener error handling
            throw e;
        }
    }

    @RabbitListener(queues = RabbitMqConstants.NOTIFICATION_PAYMENT_FAILED_QUEUE)
    public void receivePaymentFailed(PaymentCompletedEvent event) {
        findRecipientEmail(event.userId()).ifPresentOrElse(
                email -> emailService.sendPaymentFailedEmail(email, event),
                () -> log.warn("Cannot send payment failed email: recipient user {} not found for payment {}",
                        event.userId(), event.paymentId())
        );
    }

    @RabbitListener(queues = RabbitMqConstants.NOTIFICATION_TICKET_ISSUED_QUEUE)
    public void receiveTicketIssued(TicketIssuedEvent event) {
        findRecipientEmail(event.userId()).ifPresentOrElse(
                email -> emailService.sendTicketIssuedEmail(email, event),
                () -> log.warn("Cannot send ticket issued email: recipient user {} not found for order {}",
                        event.userId(), event.orderId())
        );
    }

    private Optional<String> findRecipientEmail(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId).map(User::getEmail);
    }
}
