package com.ticketbox.api.module.guestlist.domain.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "guest_import_errors", uniqueConstraints = {
        @UniqueConstraint(name = "uk_guest_import_errors_job_row_code", columnNames = { "job_id", "row_number", "error_code" })
}, indexes = {
        @Index(name = "idx_guest_import_errors_job_id", columnList = "job_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuestImportError {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "Import job cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, foreignKey = @ForeignKey(name = "fk_guest_import_errors_job"))
    private GuestImportJob importJob;

    @NotNull(message = "Row number cannot be null")
    @Min(value = 1, message = "Row number must be greater than 0")
    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", columnDefinition = "jsonb")
    private String rawData;

    @NotBlank(message = "Error code cannot be blank")
    @Size(max = 100, message = "Error code must not exceed 100 characters")
    @Column(name = "error_code", nullable = false, length = 100)
    private String errorCode;

    @NotBlank(message = "Error message cannot be blank")
    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    private void validateRowNumber() {
        if (rowNumber != null && rowNumber <= 0) {
            throw new IllegalStateException("Row number must be strictly greater than 0.");
        }
    }
}





