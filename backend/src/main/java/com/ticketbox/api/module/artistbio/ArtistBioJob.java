package com.ticketbox.api.module.artistbio;


import com.ticketbox.api.module.catalog.Concert;
import com.ticketbox.api.module.auth.User;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "artist_bio_jobs", indexes = {
        @Index(name = "idx_abj_concert_status", columnList = "concert_id, status"),
        @Index(name = "idx_abj_status_created", columnList = "status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArtistBioJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "Concert cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false, foreignKey = @ForeignKey(name = "fk_artist_bio_jobs_concert"))
    private Concert concert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", foreignKey = @ForeignKey(name = "fk_artist_bio_jobs_user"))
    private User requestedBy;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ArtistBioJobStatus status = ArtistBioJobStatus.PENDING;

    @NotBlank(message = "Source file URL cannot be blank")
    @Column(name = "source_file_url", nullable = false, columnDefinition = "TEXT")
    private String sourceFileUrl;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "generated_bio", columnDefinition = "TEXT")
    private String generatedBio;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ArtistBioJobStatus {
        PENDING,
        PROCESSING,
        DONE,
        FAILED
    }
}





