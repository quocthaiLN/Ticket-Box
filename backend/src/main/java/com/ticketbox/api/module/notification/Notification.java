package com.ticketbox.api.module.notification;

import com.ticketbox.api.module.catalog.Concert;
import com.ticketbox.api.module.ticket.Ticket;
import com.ticketbox.api.module.auth.User;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_status_created", columnList = "status, created_at"),
        @Index(name = "idx_notifications_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_notifications_concert_status", columnList = "concert_id, status"),
        @Index(name = "idx_notifications_ticket_id", columnList = "ticket_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    @NotNull(message = "Id cannot be null")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_notifications_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", foreignKey = @ForeignKey(name = "fk_notifications_concert"))
    private Concert concert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", foreignKey = @ForeignKey(name = "fk_notifications_ticket"))
    private Ticket ticket;

    @NotNull(message = "Channel cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @NotNull(message = "Type cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    @NotNull(message = "Status cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @NotBlank(message = "Payload cannot be blank")
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @NotNull(message = "Attempts count cannot be null")
    @Min(value = 0, message = "Attempts count cannot be negative")
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- Lifecycle Hooks for Validation ---

    @PrePersist
    @PreUpdate
    private void validateNotificationState() {
        if (attempts != null && attempts < 0) {
            throw new IllegalStateException("Attempts count cannot be negative.");
        }

        if (status == NotificationStatus.SENT && sentAt == null) {
            throw new IllegalStateException("Sent timestamp (sent_at) is required when status is SENT.");
        }
    }

    // --- Enums ---

    public enum NotificationChannel {
        APP,
        EMAIL,
        SMS,
        ZALO
    }

    public enum NotificationType {
        ORDER_HELD,
        ORDER_CONFIRMED,
        ORDER_CANCELLED,
        TICKET_ISSUED,
        CONCERT_REMINDER,
        SYSTEM_ALERT
    }

    public enum NotificationStatus {
        PENDING,
        SENT,
        FAILED
    }
}




