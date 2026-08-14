package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTicketTypeRequest {

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    private String description;

    @DecimalMin(value = "0.0", message = "Price must be greater than or equal to 0")
    private BigDecimal price;

    @Min(value = 1, message = "Total quantity must be greater than 0")
    @JsonProperty("total_quantity")
    private Integer totalQuantity;

    @Min(value = 1, message = "Max per user must be greater than 0")
    @JsonProperty("max_per_user")
    private Integer maxPerUser;

    @JsonProperty("sale_start_at")
    private LocalDateTime saleStartAt;

    @JsonProperty("sale_end_at")
    private LocalDateTime saleEndAt;

    private String status;
}
