package com.ticketbox.api.module.catalog.domain.entities;

import com.ticketbox.api.module.auth.domain.entities.User;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "concerts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_concerts_slug", columnNames = "slug")
}, indexes = {
        @Index(name = "idx_concerts_title", columnList = "title"),
        @Index(name = "idx_concerts_slug", columnList = "slug"),
        @Index(name = "idx_concerts_organizer_id", columnList = "organizer_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Concert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotBlank(message = "Venue cannot be blank")
    @Size(max = 255, message = "Venue must not exceed 255 characters")
    @Column(name = "venue", nullable = false, length = 255)
    private String venue;

    @NotNull(message = "Organizer cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_concerts_organizer"))
    private User organizer;

    @NotBlank(message = "Title cannot be blank")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @NotBlank(message = "Slug cannot be blank")
    @Size(max = 255, message = "Slug must not exceed 255 characters")
    @Column(name = "slug", nullable = false, length = 255)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotBlank(message = "Artist name cannot be blank")
    @Size(max = 255, message = "Artist name must not exceed 255 characters")
    @Column(name = "artist_name", nullable = false, length = 255)
    private String artistName;

    @Column(name = "artist_bio", columnDefinition = "TEXT")
    private String artistBio;

    @NotNull(message = "Starts at cannot be null")
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @NotNull(message = "Ends at cannot be null")
    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ConcertStatus status = ConcertStatus.DRAFT;

    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

    @Column(name = "seat_map_url", columnDefinition = "TEXT")
    private String seatMapUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "concert", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SeatZone> seatZones = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "concert", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketType> ticketTypes = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void validateDates() {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new IllegalStateException("Concert end date must be strictly after the start date.");
        }
    }
}
