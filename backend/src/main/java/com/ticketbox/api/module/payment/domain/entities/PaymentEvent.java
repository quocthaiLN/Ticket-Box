package com.ticketbox.api.module.payment.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payment_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_events_provider_dedupe", columnNames = { "provider", "dedupe_key" })
}, indexes = {
        @Index(name = "idx_payment_events_payment_id", columnList = "payment_id"),
        @Index(name = "idx_payment_events_status", columnList = "processing_status, received_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", foreignKey = @ForeignKey(name = "fk_payment_events_payment"))
    private Payment payment;

    @NotNull(message = "Provider cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private PaymentProvider provider;

    @NotNull(message = "Event source cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "event_source", nullable = false, length = 50)
    private PaymentEventSource eventSource;

    @NotNull(message = "Dedupe key cannot be null")
    @Size(max = 255, message = "Dedupe key must not exceed 255 characters")
    @Column(name = "dedupe_key", nullable = false, length = 255)
    private String dedupeKey;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @NotNull(message = "Raw payload cannot be null")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private String rawPayload;

    @NotNull(message = "Signature valid flag cannot be null")
    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid;

    @NotNull(message = "Processing status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 50)
    @Builder.Default
    private PaymentEventStatus processingStatus = PaymentEventStatus.RECEIVED;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
