package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AdminSeatZoneResponse {
    private UUID id;
    @JsonProperty("concert_id") private UUID concertId;
    private String code;
    private String name;
    private String description;
    private Integer capacity;
    @JsonProperty("svg_path") private String svgPath;
    @JsonProperty("sort_order") private Integer sortOrder;
}
