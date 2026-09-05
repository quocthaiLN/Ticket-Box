package com.ticketbox.api.module.auth.domain.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank(message = "Password cannot be blank")
    @Column(name = "password_hash", nullable = true, columnDefinition = "TEXT")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_account_status", nullable = false)
    @Builder.Default
    private UserAccountStatus userAccountStatus = UserAccountStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private UserProvider provider;

    @Column(name = "provider_user_id", nullable = false, columnDefinition = "TEXT")
    private String providerUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    @PreUpdate
    public void validate() {
        if (provider.equals(UserProvider.LOCAL) && (passwordHash == null || passwordHash.isBlank())) {
            throw new IllegalArgumentException("Password hash is required for LOCAL provider");
        }
    }
}