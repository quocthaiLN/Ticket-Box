package com.ticketbox.api.module.order.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotNull(message = "Concert ID is required")
    @JsonProperty("concert_id")
    private UUID concertId;

    @NotEmpty(message = "At least one order item is required")
    @Valid
    private List<CreateOrderItemRequest> items;
}
