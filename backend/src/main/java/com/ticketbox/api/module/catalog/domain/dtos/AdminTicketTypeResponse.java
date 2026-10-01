package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AdminTicketTypeResponse {
    private UUID id;
    @JsonProperty("concert_id") private UUID concertId;
    @JsonProperty("seat_zone_id") private UUID seatZoneId;
    @JsonProperty("zone_code") private String zoneCode;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    @JsonProperty("total_quantity") private Integer totalQuantity;
    @JsonProperty("held_quantity") private Integer heldQuantity;
    @JsonProperty("sold_quantity") private Integer soldQuantity;
    @JsonProperty("available_quantity") private Integer availableQuantity;
    @JsonProperty("max_per_user") private Integer maxPerUser;
    @JsonProperty("sale_start_at") private OffsetDateTime saleStartAt;
    @JsonProperty("sale_end_at") private OffsetDateTime saleEndAt;
    private String status;
}
