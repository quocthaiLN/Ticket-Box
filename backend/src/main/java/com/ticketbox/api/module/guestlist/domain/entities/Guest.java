package com.ticketbox.api.module.guestlist.domain.entities;


import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.SeatZone;
import com.ticketbox.api.module.auth.domain.entities.User;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "guest_list", uniqueConstraints = {
        @UniqueConstraint(name = "uk_guest_list_concert_phone", columnNames = { "concert_id", "phone" }),
        @UniqueConstraint(name = "uk_guest_list_concert_code", columnNames = { "concert_id", "code" })
}, indexes = {
        @Index(name = "idx_guest_list_concert_status", columnList = "concert_id, status"),
        @Index(name = "idx_guest_list_phone", columnList = "phone")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @NotNull(message = "Concert cannot be null")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false, foreignKey = @ForeignKey(name = "fk_guest_list_concert"))
    private Concert concert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_zone_id", foreignKey = @ForeignKey(name = "fk_guest_list_seat_zone"))
    private SeatZone seatZone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_job_id", foreignKey = @ForeignKey(name = "fk_guest_list_import_job"))
    private GuestImportJob importJob;

    @NotBlank(message = "Full name cannot be blank")
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @NotBlank(message = "Phone cannot be blank")
    @Size(max = 20, message = "Phone must not exceed 20 characters")
    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Size(max = 255, message = "Email must not exceed 255 characters")
    @Column(name = "email", length = 255)
    private String email;

    @Size(max = 100, message = "Code must not exceed 100 characters")
    @Column(name = "code", length = 100)
    private String code;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private GuestStatus status = GuestStatus.INVITED;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_in_by", foreignKey = @ForeignKey(name = "fk_guest_list_checker"))
    private User checkedInBy;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void validateGuestState() {
        if (status == GuestStatus.CHECKED_IN && checkedInAt == null) {
            throw new IllegalStateException("Checked in timestamp is required when status is CHECKED_IN.");
        }
    }

    public enum GuestStatus {
        INVITED,
        CHECKED_IN,
        CANCELLED
    }
}





