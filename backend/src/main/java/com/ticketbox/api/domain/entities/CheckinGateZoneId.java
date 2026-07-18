package com.ticketbox.api.domain.entities;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Vì PK của CheckinGateZone là phức hợp -> cần định nghĩa 1 class riêng để mô tả PK
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CheckinGateZoneId implements Serializable {
    @Column(name = "gate_id")
    private UUID gateId;

    @Column(name = "seat_zone_id")
    private UUID seatZoneId;
}