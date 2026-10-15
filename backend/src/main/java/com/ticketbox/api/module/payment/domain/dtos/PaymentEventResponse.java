package com.ticketbox.api.module.payment.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticketbox.api.module.payment.domain.entities.PaymentEventSource;
import com.ticketbox.api.module.payment.domain.entities.PaymentEventStatus;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Builder;

@Builder
public record PaymentEventResponse(
        UUID id,
        @JsonProperty("payment_id") UUID paymentId,
        PaymentProvider provider,
        @JsonProperty("event_source") PaymentEventSource eventSource,
        @JsonProperty("dedupe_key") String dedupeKey,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("signature_valid") Boolean signatureValid,
        @JsonProperty("processing_status") PaymentEventStatus processingStatus,
        @JsonProperty("error_message") String errorMessage,
        @JsonProperty("received_at") LocalDateTime receivedAt,
        @JsonProperty("processed_at") LocalDateTime processedAt
) {}
