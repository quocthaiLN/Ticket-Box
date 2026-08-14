package com.ticketbox.api.module.auth.producer;

import com.ticketbox.api.infrastructure.config.RabbitMqConstants;
import com.ticketbox.api.module.shared.domain.dtos.AuthOtpMessageDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendOtpMessage(AuthOtpMessageDTO message) {
        log.info("Publishing OTP message for email: {} with routing key: {}", message.getEmail(), RabbitMqConstants.AUTH_OTP_ROUTING_KEY);
        rabbitTemplate.convertAndSend(
                RabbitMqConstants.AUTH_EXCHANGE,
                RabbitMqConstants.AUTH_OTP_ROUTING_KEY,
                message
        );
        log.info("Successfully published OTP message for email: {}", message.getEmail());
    }
}
