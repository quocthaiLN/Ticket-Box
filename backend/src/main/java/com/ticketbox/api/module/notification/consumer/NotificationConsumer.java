package com.ticketbox.api.module.notification.consumer;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.notification.services.EmailService;
import com.ticketbox.api.module.shared.domain.dtos.AuthOtpMessageDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final EmailService emailService;

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
}
