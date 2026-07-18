package com.ticketbox.api.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "offline_checkin_batches", uniqueConstraints = {
        @UniqueConstraint(name = "uk_offline_checkin_batches_token", columnNames = "batch_token")
}, indexes = {
        @Index(name = "idx_ocb_concert_status", columnList = "concert_id, status"),
        @Index(name = "idx_ocb_gate_staff_status", columnList = "gate_staff_id, status"),
        @Index(name = "idx_ocb_gate_status", columnList = "gate_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfflineCheckinBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotBlank(message = "Batch token cannot be blank")
    @Size(max = 128, message = "Batch token must not exceed 128 characters")
    @Column(name = "batch_token", nullable = false, length = 128)
    private String batchToken;

    @NotNull(message = "Gate staff cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_staff_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ocb_gate_staff"))
    private CheckinGateStaff gateStaff;

    @NotNull(message = "Staff cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ocb_staff"))
    private User staff;

    @NotNull(message = "Concert cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ocb_concert"))
    private Concert concert;

    @NotNull(message = "Gate cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(value = {
            @JoinColumn(name = "gate_id", referencedColumnName = "id", nullable = false),
            @JoinColumn(name = "concert_id", referencedColumnName = "concert_id", insertable = false, updatable = false)
    }, foreignKey = @ForeignKey(name = "fk_ocb_gate_composite"))
    private CheckinGate gate;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OfflineBatchStatus status = OfflineBatchStatus.PENDING;

    @NotNull(message = "Item count cannot be null")
    @Min(value = 0, message = "Item count cannot be negative")
    @Column(name = "item_count", nullable = false)
    @Builder.Default
    private Integer itemCount = 0;

    @NotNull(message = "Accepted count cannot be null")
    @Min(value = 0, message = "Accepted count cannot be negative")
    @Column(name = "accepted_count", nullable = false)
    @Builder.Default
    private Integer acceptedCount = 0;

    @NotNull(message = "Conflict count cannot be null")
    @Min(value = 0, message = "Conflict count cannot be negative")
    @Column(name = "conflict_count", nullable = false)
    @Builder.Default
    private Integer conflictCount = 0;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void validateCounters() {
        if (itemCount != null && itemCount < 0) {
            throw new IllegalStateException("Item count cannot be negative.");
        }
        if (acceptedCount != null && acceptedCount < 0) {
            throw new IllegalStateException("Accepted count cannot be negative.");
        }
        if (conflictCount != null && conflictCount < 0) {
            throw new IllegalStateException("Conflict count cannot be negative.");
        }
    }

    public enum OfflineBatchStatus {
        PENDING,
        DONE
    }
}
