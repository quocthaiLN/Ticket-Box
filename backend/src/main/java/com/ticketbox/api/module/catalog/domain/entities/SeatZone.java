package com.ticketbox.api.module.catalog.domain.entities;

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
@Table(name = "seat_zones", uniqueConstraints = {
        @UniqueConstraint(name = "uk_seat_zones_concert_code", columnNames = { "concert_id", "code" }),
        @UniqueConstraint(name = "uk_seat_zones_id_concert", columnNames = { "id", "concert_id" })
}, indexes = {
        @Index(name = "idx_seat_zones_concert_id", columnList = "concert_id"),
        @Index(name = "idx_seat_zones_code", columnList = "code"),
        @Index(name = "idx_seat_zones_name", columnList = "name"),
        @Index(name = "idx_seat_zones_concert_sort", columnList = "concert_id, sort_order")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatZone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "Concert cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false, foreignKey = @ForeignKey(name = "fk_seat_zones_concert"))
    private Concert concert;

    @NotBlank(message = "Zone code cannot be blank")
    @Size(max = 50, message = "Zone code must not exceed 50 characters")
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @NotBlank(message = "Zone name cannot be blank")
    @Size(max = 100, message = "Zone name must not exceed 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Capacity cannot be null")
    @Min(value = 1, message = "Capacity must be greater than 0")
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "svg_path", columnDefinition = "TEXT")
    private String svgPath;

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

    // --- Lifecycle Hooks for Validation ---

    @PrePersist
    @PreUpdate
    private void validateCapacity() {
        if (capacity != null && capacity <= 0) {
            throw new IllegalStateException("Capacity must be strictly greater than 0.");
        }
    }
}