package com.ticketbox.api.module.order.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeldOrderResponse {

    @JsonProperty("order_id")
    private UUID orderId;

    @JsonProperty("concert_id")
    private UUID concertId;

    private String status;
    private List<HeldOrderItemResponse> items;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    private String currency;

    @JsonProperty("hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
