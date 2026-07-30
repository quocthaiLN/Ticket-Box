package com.ticketbox.api.module.order;


import com.ticketbox.api.module.payment.Payment;
import com.ticketbox.api.module.catalog.Concert;
import com.ticketbox.api.module.ticket.Ticket;
import com.ticketbox.api.module.auth.User;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_orders_idempotency_key", columnNames = "idempotency_key")
}, indexes = {
        @Index(name = "idx_orders_user_status_created", columnList = "user_id, status, created_at"),
        @Index(name = "idx_orders_concert_status", columnList = "concert_id, status"),
        @Index(name = "idx_orders_hold_expires_at", columnList = "hold_expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "User cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_user"))
    private User user;

    @NotNull(message = "Concert cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false, foreignKey = @ForeignKey(name = "fk_orders_concert"))
    private Concert concert;

    @NotBlank(message = "Idempotency key cannot be blank")
    @Size(max = 128, message = "Idempotency key must not exceed 128 characters")
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.HELD;

    @NotNull(message = "Total amount cannot be null")
    @DecimalMin(value = "0.0", message = "Total amount must be greater than or equal to 0")
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @NotBlank(message = "Currency cannot be blank")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @Builder.Default
    private String currency = "VND";

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "cancelled_reason", columnDefinition = "TEXT")
    private String cancelledReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- Relationships ---

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Payment> payments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Ticket> tickets = new ArrayList<>();

    // --- Lifecycle Hooks for Validation ---

    @PrePersist
    @PreUpdate
    private void validateOrderStatusRules() {
        if (totalAmount != null && totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Total amount cannot be negative.");
        }

        if (status == OrderStatus.HELD && holdExpiresAt == null) {
            throw new IllegalStateException("Hold expiration time is required when order status is HELD.");
        }
        if (status == OrderStatus.CONFIRMED && confirmedAt == null) {
            throw new IllegalStateException("Confirmation time is required when order status is CONFIRMED.");
        }
        if (status == OrderStatus.CANCELLED && cancelledAt == null) {
            throw new IllegalStateException("Cancellation time is required when order status is CANCELLED.");
        }
        if (status == OrderStatus.EXPIRED && expiredAt == null) {
            throw new IllegalStateException("Expiration time is required when order status is EXPIRED.");
        }
    }

    // --- Enums ---

    public enum OrderStatus {
        HELD,
        CONFIRMED,
        CANCELLED,
        EXPIRED
    }
}




