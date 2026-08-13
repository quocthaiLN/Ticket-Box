package com.ticketbox.api.infrastructure.config;

public final class RabbitMqConstants {

    private RabbitMqConstants() {
        // Restrict instantiation
    }

    // Auth Module Constants
    public static final String AUTH_EXCHANGE = "auth.exchange";
    public static final String AUTH_OTP_QUEUE = "auth.email.otp.queue";
    public static final String AUTH_OTP_ROUTING_KEY = "auth.email.otp";

    // Standard Template DLX & DLQ Constants (for future module reference)
    public static final String DEFAULT_DLX = "ticketbox.dlx";
    public static final String DEFAULT_DLQ_ROUTING_KEY = "ticketbox.dlk";
}
