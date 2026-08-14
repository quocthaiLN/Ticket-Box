package com.ticketbox.api.module.catalog.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "ticket_types",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_ticket_types_concert_name", columnNames = {"concert_id", "name"}),
        @UniqueConstraint(name = "uk_ticket_types_id_concert", columnNames = {"id", "concert_id"})
    },
    indexes = {
        @Index(name = "idx_ticket_types_concert_status", columnList = "concert_id, status"),
        @Index(name = "idx_ticket_types_sale_window", columnList = "sale_start_at, sale_end_at"),
        @Index(name = "idx_ticket_types_seat_zone", columnList = "seat_zone_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "Concert cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "concert_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_ticket_types_concert")
    )
    private Concert concert;

    @NotNull(message = "Seat zone cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "seat_zone_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_ticket_types_seat_zone")
    )
    private SeatZone seatZone;

    @NotBlank(message = "Ticket type name cannot be blank")
    @Size(max = 100, message = "Ticket type name must not exceed 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Price cannot be null")
    @DecimalMin(value = "0.0", message = "Price must be greater than or equal to 0")
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @NotBlank(message = "Currency cannot be blank")
    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "VND";

    @NotNull(message = "Total quantity cannot be null")
    @Min(value = 0, message = "Total quantity cannot be negative")
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @NotNull(message = "Held quantity cannot be null")
    @Min(value = 0, message = "Held quantity cannot be negative")
    @Column(name = "held_quantity", nullable = false)
    @Builder.Default
    private Integer heldQuantity = 0;

    @NotNull(message = "Sold quantity cannot be null")
    @Min(value = 0, message = "Sold quantity cannot be negative")
    @Column(name = "sold_quantity", nullable = false)
    @Builder.Default
    private Integer soldQuantity = 0;

    @NotNull(message = "Max per user cannot be null")
    @Min(value = 1, message = "Max per user must be greater than 0")
    @Column(name = "max_per_user", nullable = false)
    private Integer maxPerUser;

    @NotNull(message = "Sale start time cannot be null")
    @Column(name = "sale_start_at", nullable = false)
    private LocalDateTime saleStartAt;

    @NotNull(message = "Sale end time cannot be null")
    @Column(name = "sale_end_at", nullable = false)
    private LocalDateTime saleEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TicketTypeStatus status = TicketTypeStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- Transient / Derived Property ---

    /**
     * Tính toán số lượng vé còn lại có thể phân phối một cách động.
     * Thuộc tính này không được lưu trữ vật lý trong Database để tránh Race Condition.
     */
    @Transient
    public int getAvailableQuantity() {
        return this.totalQuantity - this.heldQuantity - this.soldQuantity;
    }

    // --- Lifecycle Hooks for Validation ---

    @PrePersist
    @PreUpdate
    private void validateBusinessRules() {
        // 1. Kiểm tra Sale Window
        if (saleStartAt != null && saleEndAt != null && !saleEndAt.isAfter(saleStartAt)) {
            throw new IllegalStateException("Sale end date must be strictly after the sale start date.");
        }

        // 2. Kiểm tra tính toàn vẹn của tồn kho (Chống Oversell ở mức thực thể)
        if (totalQuantity != null && heldQuantity != null && soldQuantity != null) {
            if (totalQuantity < (heldQuantity + soldQuantity)) {
                throw new IllegalStateException(
                    String.format("Inventory violation: total_quantity (%d) is less than held (%d) + sold (%d). Available would be negative.", 
                    totalQuantity, heldQuantity, soldQuantity)
                );
            }
        }
    }

    // --- Enums ---

    public enum TicketTypeStatus {
        DRAFT,
        ACTIVE,
        SUSPENDED,
        CLOSED,
        SOLD_OUT
    }
}
