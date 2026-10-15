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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "refunds", uniqueConstraints = {
        @UniqueConstraint(name = "uk_refunds_idempotency_key", columnNames = "idempotency_key")
}, indexes = {
        @Index(name = "idx_refunds_payment_id", columnList = "payment_id"),
        @Index(name = "idx_refunds_status", columnList = "status, requested_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "Payment cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_refunds_payment"))
    private Payment payment;

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be strictly greater than 0")
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @NotBlank(message = "Currency cannot be blank")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @NotBlank(message = "Idempotency key cannot be blank")
    @Size(max = 128, message = "Idempotency key must not exceed 128 characters")
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private RefundStatus status = RefundStatus.REQUESTED;

    @Size(max = 255, message = "Provider refund ID must not exceed 255 characters")
    @Column(name = "provider_refund_id", length = 255)
    private String providerRefundId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @NotNull(message = "Requested by user/system ID cannot be null")
    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Size(max = 100, message = "Failure code must not exceed 100 characters")
    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
