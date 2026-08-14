package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {

    @JsonProperty("concert_id")
    private UUID concertId;

    @JsonProperty("as_of")
    private LocalDateTime asOf;

    private List<InventoryItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryItem {
        @JsonProperty("ticket_type_id")
        private UUID ticketTypeId;

        @JsonProperty("seat_zone_id")
        private UUID seatZoneId;

        @JsonProperty("zone_code")
        private String zoneCode;

        @JsonProperty("available_quantity")
        private Integer availableQuantity;

        private String status;

        @JsonProperty("display_status")
        private String displayStatus;
    }
}
