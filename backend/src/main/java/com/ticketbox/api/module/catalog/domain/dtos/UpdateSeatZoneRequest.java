package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSeatZoneRequest {

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        UnknownAdminField.reject(field);
    }

    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Builder.Default
    private JsonNullable<String> name = JsonNullable.undefined();

    @Builder.Default
    private JsonNullable<String> description = JsonNullable.undefined();

    @Min(value = 1, message = "Capacity must be strictly greater than 0")
    @Builder.Default
    private JsonNullable<Integer> capacity = JsonNullable.undefined();

    @JsonProperty("svg_path")
    @Builder.Default
    private JsonNullable<String> svgPath = JsonNullable.undefined();

    @JsonProperty("sort_order")
    @Builder.Default
    private JsonNullable<Integer> sortOrder = JsonNullable.undefined();
}
