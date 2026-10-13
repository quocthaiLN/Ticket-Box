package com.ticketbox.api.module.audit.domain.entities;

import com.ticketbox.api.module.auth.domain.entities.User;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity biểu diễn bản ghi Audit Log bất biến (Immutable Audit Trail).
 * Mọi trường đều có updatable = false để ngăn chặn cập nhật ở tầng JPA.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_logs_actor_created", columnList = "actor_user_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", updatable = false, foreignKey = @ForeignKey(name = "fk_audit_logs_actor"))
    private User actor;

    @NotNull(message = "Action cannot be null")
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 100, updatable = false)
    private AuditAction action;

    @NotNull(message = "Entity type cannot be null")
    @Size(max = 100, message = "Entity type must not exceed 100 characters")
    @Column(name = "entity_type", nullable = false, length = 100, updatable = false)
    private String entityType;

    @Size(max = 100, message = "Entity id must not exceed 100 characters")
    @Column(name = "entity_id", length = 100, updatable = false)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb", updatable = false)
    private String metadata;

    @Size(max = 45, message = "IP address must not exceed 45 characters")
    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT", updatable = false)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
