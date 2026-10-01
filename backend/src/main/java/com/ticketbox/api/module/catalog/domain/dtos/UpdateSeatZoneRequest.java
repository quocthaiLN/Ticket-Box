package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashSet;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSeatZoneRequest {

    @JsonIgnore private Set<String> suppliedFields = new HashSet<>();
    @JsonIgnore public boolean wasSupplied(String field) { return suppliedFields != null && suppliedFields.contains(field); }
    @com.fasterxml.jackson.annotation.JsonAnySetter public void rejectUnknown(String field, Object value) { UnknownAdminField.reject(field); }
    public void setName(String value) { name = value; suppliedFields.add("name"); }
    public void setDescription(String value) { description = value; suppliedFields.add("description"); }
    public void setCapacity(Integer value) { capacity = value; suppliedFields.add("capacity"); }
    @JsonProperty("svg_path") public void setSvgPath(String value) { svgPath = value; suppliedFields.add("svg_path"); }
    @JsonProperty("sort_order") public void setSortOrder(Integer value) { sortOrder = value; suppliedFields.add("sort_order"); }

    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    private String description;

    @Min(value = 1, message = "Capacity must be strictly greater than 0")
    private Integer capacity;

    @JsonProperty("svg_path")
    private String svgPath;

    @JsonProperty("sort_order")
    private Integer sortOrder;
}
