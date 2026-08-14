package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketTypeRequest {

    @NotNull(message = "Seat zone ID is required")
    @JsonProperty("seat_zone_id")
    private UUID seatZoneId;

    @NotBlank(message = "Ticket type name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be greater than or equal to 0")
    private BigDecimal price;

    @Size(min = 3, max = 3, message = "Currency must be exactly 3 characters")
    @Builder.Default
    private String currency = "VND";

    @NotNull(message = "Total quantity is required")
    @Min(value = 1, message = "Total quantity must be greater than 0")
    @JsonProperty("total_quantity")
    private Integer totalQuantity;

    @NotNull(message = "Max per user is required")
    @Min(value = 1, message = "Max per user must be greater than 0")
    @JsonProperty("max_per_user")
    private Integer maxPerUser;

    @NotNull(message = "Sale start time is required")
    @JsonProperty("sale_start_at")
    private LocalDateTime saleStartAt;

    @NotNull(message = "Sale end time is required")
    @JsonProperty("sale_end_at")
    private LocalDateTime saleEndAt;
}
