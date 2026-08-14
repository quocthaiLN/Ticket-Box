package com.ticketbox.api.module.order.domain.entities;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_ticket_type_counters", indexes = {
        @Index(name = "idx_uttc_ticket_type_id", columnList = "ticket_type_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTicketTypeCounter {

    @EmbeddedId
    private UserTicketTypeCounterId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_uttc_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("ticketTypeId")
    @JoinColumn(name = "ticket_type_id", nullable = false, foreignKey = @ForeignKey(name = "fk_uttc_ticket_type"))
    private TicketType ticketType;

    @NotNull(message = "Held quantity cannot be null")
    @Min(value = 0, message = "Held quantity cannot be negative")
    @Column(name = "held_quantity", nullable = false)
    @Builder.Default
    private Integer heldQuantity = 0;

    @NotNull(message = "Paid quantity cannot be null")
    @Min(value = 0, message = "Paid quantity cannot be negative")
    @Column(name = "paid_quantity", nullable = false)
    @Builder.Default
    private Integer paidQuantity = 0;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    private void validateQuantities() {
        if (heldQuantity != null && heldQuantity < 0) {
            throw new IllegalStateException("Held quantity cannot be negative.");
        }
        if (paidQuantity != null && paidQuantity < 0) {
            throw new IllegalStateException("Paid quantity cannot be negative.");
        }
    }
}
