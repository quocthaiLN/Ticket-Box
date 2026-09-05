package com.ticketbox.api.module.order.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeldOrderItemResponse {

    @JsonProperty("ticket_type_id")
    private UUID ticketTypeId;

    private Integer quantity;

    @JsonProperty("unit_price")
    private BigDecimal unitPrice;

    @JsonProperty("line_total")
    private BigDecimal lineTotal;
}
