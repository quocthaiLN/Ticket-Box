package com.ticketbox.api.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "checkin_gate_staff", indexes = {
        @Index(name = "idx_cgs_staff_status", columnList = "staff_id, status"),
        @Index(name = "idx_cgs_concert_gate_status", columnList = "concert_id, gate_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckinGateStaff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "Staff cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false, foreignKey = @ForeignKey(name = "fk_checkin_gate_staff_user"))
    private User staff;

    @NotNull(message = "Concert cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false, foreignKey = @ForeignKey(name = "fk_checkin_gate_staff_concert"))
    private Concert concert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "gate_id", referencedColumnName = "id"),
            @JoinColumn(name = "concert_id", referencedColumnName = "concert_id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_checkin_gate_staff_gate_composite"))
    private CheckinGate gate;

    @Size(max = 255, message = "Name must not exceed 255 characters")
    @Column(name = "name", length = 255)
    private String name;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StaffStatus status = StaffStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum StaffStatus {
        ACTIVE,
        INACTIVE
    }
}
