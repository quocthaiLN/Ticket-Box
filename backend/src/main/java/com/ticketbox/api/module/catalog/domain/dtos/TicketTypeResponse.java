package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class TicketTypeResponse {

    private UUID id;

    @JsonProperty("concert_id")
    private UUID concertId;

    @JsonProperty("seat_zone_id")
    private UUID seatZoneId;

    @JsonProperty("zone_code")
    private String zoneCode;

    private String name;
    private String description;
    private BigDecimal price;
    private String currency;

    @JsonProperty("total_quantity")
    private Integer totalQuantity;

    @JsonProperty("held_quantity")
    private Integer heldQuantity;

    @JsonProperty("sold_quantity")
    private Integer soldQuantity;

    @JsonProperty("available_quantity")
    private Integer availableQuantity;

    @JsonProperty("max_per_user")
    private Integer maxPerUser;

    @JsonProperty("sale_start_at")
    private LocalDateTime saleStartAt;

    @JsonProperty("sale_end_at")
    private LocalDateTime saleEndAt;

    private String status;
}
