package com.ticketbox.api.infrastructure.config;

public final class RabbitMqConstants {

    private RabbitMqConstants() {
        // Restrict instantiation
    }

    // Auth Module Constants
    public static final String AUTH_EXCHANGE = "auth.exchange";
    public static final String AUTH_OTP_QUEUE = "auth.email.otp.queue";
    public static final String AUTH_OTP_ROUTING_KEY = "auth.email.otp";

    // Catalog Module Constants
    public static final String CATALOG_EXCHANGE = "catalog.exchange";
    public static final String CATALOG_CACHE_WARMUP_QUEUE = "catalog.cache-warmup.queue";
    public static final String CATALOG_CACHE_WARMUP_ROUTING_KEY = "inventory.cache-warmup";

    // Standard Template DLX & DLQ Constants (for future module reference)
    public static final String DEFAULT_DLX = "ticketbox.dlx";
    public static final String DEFAULT_DLQ_ROUTING_KEY = "ticketbox.dlk";
}
