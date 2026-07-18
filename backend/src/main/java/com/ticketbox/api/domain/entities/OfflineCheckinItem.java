package com.ticketbox.api.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "offline_checkin_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_oci_batch_qr_hash", columnNames = { "batch_id", "qr_token_hash" })
}, indexes = {
        @Index(name = "idx_oci_batch_result", columnList = "batch_id, result"),
        @Index(name = "idx_oci_ticket_id", columnList = "ticket_id"),
        @Index(name = "idx_oci_guest_id", columnList = "guest_id"),
        @Index(name = "idx_oci_gate_id", columnList = "gate_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflineCheckinItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "Batch cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_oci_batch"))
    private OfflineCheckinBatch batch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", foreignKey = @ForeignKey(name = "fk_oci_ticket"))
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", foreignKey = @ForeignKey(name = "fk_oci_guest"))
    private Guest guest;

    @Size(max = 255, message = "QR token hash must not exceed 255 characters")
    @Column(name = "qr_token_hash", length = 255)
    private String qrTokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_id", foreignKey = @ForeignKey(name = "fk_oci_gate"))
    private CheckinGate gate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_zone_id", foreignKey = @ForeignKey(name = "fk_oci_seat_zone"))
    private SeatZone seatZone;

    @NotNull(message = "Result cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    @Builder.Default
    private OfflineItemStatus result = OfflineItemStatus.PENDING;

    @Size(max = 100, message = "Error code must not exceed 100 characters")
    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @NotNull(message = "Scanned at cannot be null")
    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    private void validateTarget() {
        if (qrTokenHash == null && ticket == null && guest == null) {
            throw new IllegalStateException("At least one target (qr_token_hash, ticket_id, or guest_id) must be specified.");
        }
    }

    public enum OfflineItemStatus {
        PENDING,
        ACCEPTED,
        CONFLICT,
        WRONG_GATE,
        INVALID
    }
}
