package com.ticketbox.api.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "guest_import_jobs", indexes = {
        @Index(name = "idx_guest_import_jobs_concert_status", columnList = "concert_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "Concert cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false, foreignKey = @ForeignKey(name = "fk_guest_import_jobs_concert"))
    private Concert concert;

    @NotNull(message = "Uploaded by cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false, foreignKey = @ForeignKey(name = "fk_guest_import_jobs_user"))
    private User uploadedBy;

    @NotBlank(message = "File URL cannot be blank")
    @Column(name = "file_url", nullable = false, columnDefinition = "TEXT")
    private String fileUrl;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ImportStatus status = ImportStatus.PENDING;

    @NotNull(message = "Total rows cannot be null")
    @Min(value = 0, message = "Total rows cannot be negative")
    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private Integer totalRows = 0;

    @NotNull(message = "Success rows cannot be null")
    @Min(value = 0, message = "Success rows cannot be negative")
    @Column(name = "success_rows", nullable = false)
    @Builder.Default
    private Integer successRows = 0;

    @NotNull(message = "Error rows cannot be null")
    @Min(value = 0, message = "Error rows cannot be negative")
    @Column(name = "error_rows", nullable = false)
    @Builder.Default
    private Integer errorRows = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void validateCounters() {
        if (totalRows != null && totalRows < 0) {
            throw new IllegalStateException("Total rows count cannot be negative.");
        }
        if (successRows != null && successRows < 0) {
            throw new IllegalStateException("Success rows count cannot be negative.");
        }
        if (errorRows != null && errorRows < 0) {
            throw new IllegalStateException("Error rows count cannot be negative.");
        }
    }

    public enum ImportStatus {
        PENDING,
        PROCESSING,
        DONE,
        PARTIAL,
        FAILED
    }
}
