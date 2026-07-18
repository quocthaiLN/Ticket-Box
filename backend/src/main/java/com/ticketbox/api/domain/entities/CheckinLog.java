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
@Table(name = "checkin_logs", indexes = {
        @Index(name = "idx_checkin_logs_ticket_scanned", columnList = "ticket_id, scanned_at"),
        @Index(name = "idx_checkin_logs_concert_scanned", columnList = "concert_id, scanned_at"),
        @Index(name = "idx_checkin_logs_gate_scanned", columnList = "gate_id, scanned_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckinLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", foreignKey = @ForeignKey(name = "fk_checkin_logs_ticket"))
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", foreignKey = @ForeignKey(name = "fk_checkin_logs_guest"))
    private Guest guest;

    @NotNull(message = "Concert cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false, foreignKey = @ForeignKey(name = "fk_checkin_logs_concert"))
    private Concert concert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "seat_zone_id", referencedColumnName = "id"),
            @JoinColumn(name = "concert_id", referencedColumnName = "concert_id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_checkin_logs_seat_zone_composite"))
    private SeatZone seatZone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "gate_id", referencedColumnName = "id"),
            @JoinColumn(name = "concert_id", referencedColumnName = "concert_id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_checkin_logs_gate_composite"))
    private CheckinGate gate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", foreignKey = @ForeignKey(name = "fk_checkin_logs_staff"))
    private User staff;

    @Size(max = 255, message = "Scan token hash must not exceed 255 characters")
    @Column(name = "scan_token_hash", length = 255)
    private String scanTokenHash;

    @NotNull(message = "Result cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 50)
    private CheckinResult result;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @NotNull(message = "Scanned at cannot be null")
    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum CheckinResult {
        SUCCESS,
        WRONG_CONCERT,
        WRONG_GATE,
        INVALID_TICKET,
        ALREADY_CHECKED_IN
    }
}
