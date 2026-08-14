package com.ticketbox.api.module.order.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ticketbox.api.module.ticket.domain.entities.Ticket;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;

@Entity
@Table(name = "order_items", indexes = {
        @Index(name = "idx_order_items_order_id", columnList = "order_id"),
        @Index(name = "idx_order_items_ticket_type_id", columnList = "ticket_type_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "Order cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_items_order"))
    private Order order;

    @NotNull(message = "Ticket type cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_type_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_items_ticket_type"))
    private TicketType ticketType;

    @NotNull(message = "Quantity cannot be null")
    @Min(value = 1, message = "Quantity must be strictly greater than 0")
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @NotNull(message = "Unit price cannot be null")
    @DecimalMin(value = "0.0", message = "Unit price must be greater than or equal to 0")
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @NotNull(message = "Line total cannot be null")
    @DecimalMin(value = "0.0", message = "Line total must be greater than or equal to 0")
    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // --- Relationships ---

    @Builder.Default
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Ticket> tickets = new ArrayList<>();

    // --- Lifecycle Hooks for Validation ---

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        validateAndCalculateItem();
    }

    @PreUpdate
    private void validateAndCalculateItem() {
        if (quantity != null && quantity <= 0) {
            throw new IllegalStateException("Quantity must be strictly greater than 0.");
        }
        if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Unit price cannot be negative.");
        }

        // Tự động tính toán / Validate line_total = quantity * unit_price
        if (quantity != null && unitPrice != null) {
            BigDecimal expectedTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            if (this.lineTotal == null) {
                this.lineTotal = expectedTotal;
            } else if (this.lineTotal.compareTo(expectedTotal) != 0) {
                throw new IllegalStateException(
                        "Line total violation: line_total must equal quantity multiplied by unit_price.");
            }
        }

        // Bổ sung trigger bảo vệ tầng ứng dụng: TicketType phải thuộc cùng Concert với
        // Order
        if (order != null && ticketType != null) {
            if (order.getConcert() != null && ticketType.getConcert() != null) {
                if (!order.getConcert().getId().equals(ticketType.getConcert().getId())) {
                    throw new IllegalStateException(
                            "Context violation: TicketType must belong to the same Concert as the parent Order.");
                }
            }
        }
    }
}
