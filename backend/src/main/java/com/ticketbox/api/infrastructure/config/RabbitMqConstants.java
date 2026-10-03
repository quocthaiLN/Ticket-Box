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

    // Artist Bio Module Constants
    public static final String ARTIST_BIO_EXCHANGE = "artist-bio.exchange";
    public static final String ARTIST_BIO_QUEUE = "q.artist.bio-generation";
    public static final String ARTIST_BIO_ROUTING_KEY = "artist-bio.generate";
    public static final String ARTIST_BIO_RETRY_QUEUE = "q.artist.bio-generation.retry";
    public static final String ARTIST_BIO_RETRY_KEY = "artist-bio.retry";
    public static final String ARTIST_BIO_DLX = "artist-bio.dlx";
    public static final String ARTIST_BIO_DLQ = "q.artist.bio-generation.dead";
    public static final String ARTIST_BIO_DEAD_KEY = "artist-bio.dead";
    public static final String ARTIST_BIO_LISTENER_FACTORY = "artistBioListenerFactory";

    // Payment Module Constants
    public static final String PAYMENT_EXCHANGE = "payment.exchange";

    public static final String PAYMENT_SUCCEEDED_ROUTING_KEY = "payment.succeeded";
    public static final String PAYMENT_FAILED_ROUTING_KEY = "payment.failed";
    public static final String TICKET_PAYMENT_SUCCEEDED_QUEUE = "ticket.payment-succeeded.queue";
    public static final String NOTIFICATION_PAYMENT_FAILED_QUEUE = "notification.payment-failed.queue";

    // Ticket Module Constants
    public static final String TICKET_EXCHANGE = "ticket.exchange";
    public static final String TICKET_ISSUED_ROUTING_KEY = "ticket.issued";
    public static final String NOTIFICATION_TICKET_ISSUED_QUEUE = "notification.ticket-issued.queue";

    // Standard Template DLX & DLQ Constants (for future module reference)
    public static final String DEFAULT_DLX = "ticketbox.dlx";
    public static final String DEFAULT_DLQ_ROUTING_KEY = "ticketbox.dlk";
}
