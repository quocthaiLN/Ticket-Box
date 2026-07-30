package com.ticketbox.api.module.payment;


import com.ticketbox.api.module.order.Order;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_idempotency_key", columnNames = "idempotency_key"),
        @UniqueConstraint(name = "uk_payments_provider_tx", columnNames = { "provider", "provider_transaction_id" })
}, indexes = {
        @Index(name = "idx_payments_order_status", columnList = "order_id, status"),
        @Index(name = "idx_payments_status_created", columnList = "status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "Order cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_payments_order"))
    private Order order;

    @NotNull(message = "Provider cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 50)
    private PaymentProvider provider;

    @Size(max = 255, message = "Provider transaction ID must not exceed 255 characters")
    @Column(name = "provider_transaction_id", length = 255)
    private String providerTransactionId;

    @NotBlank(message = "Idempotency key cannot be blank")
    @Size(max = 128, message = "Idempotency key must not exceed 128 characters")
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be strictly greater than 0")
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @NotBlank(message = "Currency cannot be blank")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @Builder.Default
    private String currency = "VND";

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "checkout_url", columnDefinition = "TEXT")
    private String checkoutUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_payload", columnDefinition = "jsonb")
    private String providerPayload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "webhook_payload", columnDefinition = "jsonb")
    private String webhookPayload;

    @Column(name = "webhook_received_at")
    private LocalDateTime webhookReceivedAt;

    @Column(name = "webhook_signature_valid")
    private Boolean webhookSignatureValid;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- Lifecycle Hooks for Validation ---

    @PrePersist
    @PreUpdate
    private void validatePaymentState() {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Payment amount must be strictly greater than 0.");
        }

        if (status == PaymentStatus.SUCCEEDED && paidAt == null) {
            throw new IllegalStateException(
                    "Payment confirmation time (paid_at) is required when status is SUCCEEDED.");
        }
    }

    // --- Enums ---

    public enum PaymentProvider {
        VNPAY,
        MOMO,
        STRIPE,
        ALIPAY
    }

    public enum PaymentStatus {
        PENDING,
        SUCCEEDED,
        FAILED,
        EXPIRED
    }
}




