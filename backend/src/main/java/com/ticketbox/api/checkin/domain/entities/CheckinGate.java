package com.ticketbox.api.checkin.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.ticketbox.api.concert.domain.entities.Concert;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "checkin_gates",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_checkin_gates_concert_code", columnNames = {"concert_id", "code"}),
        @UniqueConstraint(name = "uk_checkin_gates_id_concert", columnNames = {"id", "concert_id"})
    },
    indexes = {
        @Index(name = "idx_checkin_gates_concert_active", columnList = "concert_id, is_active")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckinGate {

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
        foreignKey = @ForeignKey(name = "fk_checkin_gates_concert")
    )
    private Concert concert;

    @NotBlank(message = "Gate code cannot be blank")
    @Size(max = 50, message = "Gate code must not exceed 50 characters")
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @NotBlank(message = "Gate name cannot be blank")
    @Size(max = 255, message = "Gate name must not exceed 255 characters")
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Status cannot be null")
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @NotNull(message = "Sort order cannot be null")
    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- Relationships ---

    @Builder.Default
    @OneToMany(mappedBy = "gate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CheckinGateZone> gateZones = new ArrayList<>();
}




