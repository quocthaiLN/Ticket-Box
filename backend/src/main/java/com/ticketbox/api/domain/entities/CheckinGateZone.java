package com.ticketbox.api.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "checkin_gate_zones", indexes = {
        @Index(name = "idx_cgz_seat_zone_id", columnList = "seat_zone_id"),
        @Index(name = "idx_cgz_concert_id", columnList = "concert_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckinGateZone {

    @EmbeddedId
    private CheckinGateZoneId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("gateId")
    @JoinColumn(name = "gate_id", nullable = false)
    private CheckinGate gate;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("seatZoneId")
    @JoinColumn(name = "seat_zone_id", nullable = false)
    private SeatZone seatZone;

    @NotNull(message = "Concert ID cannot be null")
    @Column(name = "concert_id", nullable = false)
    private UUID concertId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}