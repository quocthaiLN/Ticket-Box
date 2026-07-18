package com.ticketbox.api.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tickets_qr_token_hash", columnNames = "qr_token_hash")
}, indexes = {
        @Index(name = "idx_tickets_user_concert_status", columnList = "user_id, concert_id, status"),
        @Index(name = "idx_tickets_user_ticket_type_status", columnList = "user_id, ticket_type_id, status"),
        @Index(name = "idx_tickets_ticket_type_status", columnList = "ticket_type_id, status"),
        @Index(name = "idx_tickets_order_id", columnList = "order_id"),
        @Index(name = "idx_tickets_order_item_id", columnList = "order_item_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "Order cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_order"))
    private Order order;

    @NotNull(message = "Order item cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_order_item"))
    private OrderItem orderItem;

    @NotNull(message = "User cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_user"))
    private User user;

    @NotNull(message = "Concert cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tickets_concert"))
    private Concert concert;

    @NotNull(message = "Ticket type cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "ticket_type_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "concert_id", referencedColumnName = "concert_id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_tickets_ticket_type_composite"))
    private TicketType ticketType;

    @NotNull(message = "Seat zone cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "seat_zone_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "concert_id", referencedColumnName = "concert_id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_tickets_seat_zone_composite"))
    private SeatZone seatZone;

    @NotBlank(message = "QR token hash cannot be blank")
    @Size(max = 255, message = "QR token hash must not exceed 255 characters")
    @Column(name = "qr_token_hash", nullable = false, length = 255)
    private String qrTokenHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "qr_payload", columnDefinition = "jsonb")
    private String qrPayload;

    @Column(name = "qr_signature", columnDefinition = "TEXT")
    private String qrSignature;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.ISSUED;

    @NotNull(message = "Issued at cannot be null")
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_in_by", foreignKey = @ForeignKey(name = "fk_tickets_checker"))
    private User checkedInBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- Lifecycle Hooks for Validation ---

    @PrePersist
    @PreUpdate
    private void validateTicketState() {
        if (status == TicketStatus.CHECKED_IN && checkedInAt == null) {
            throw new IllegalStateException("Check-in time (checked_in_at) is required when status is CHECKED_IN.");
        }
    }

    // --- Enums ---

    public enum TicketStatus {
        ISSUED,
        CHECKED_IN,
        REFUNDED,
        INVALIDATED
    }
}