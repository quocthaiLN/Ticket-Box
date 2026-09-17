package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTypeQuotaResponse {

    @JsonProperty("ticket_type_id")
    private UUID ticketTypeId;

    @JsonProperty("held_quantity")
    private Integer heldQuantity;

    @JsonProperty("max_per_user")
    private Integer maxPerUser;

    @JsonProperty("paid_quantity")
    private Integer paidQuantity;

    @JsonProperty("remaining_quantity")
    private Integer remainingQuantity;
}
