package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTicketTypeRequest {

    @JsonIgnore private Set<String> suppliedFields = new HashSet<>();
    @JsonIgnore public boolean wasSupplied(String field) { return suppliedFields != null && suppliedFields.contains(field); }
    @com.fasterxml.jackson.annotation.JsonAnySetter public void rejectUnknown(String field, Object value) { UnknownAdminField.reject(field); }
    public void setName(String value) { name = value; suppliedFields.add("name"); }
    public void setDescription(String value) { description = value; suppliedFields.add("description"); }
    public void setPrice(BigDecimal value) { price = value; suppliedFields.add("price"); }
    @JsonProperty("total_quantity") public void setTotalQuantity(Integer value) { totalQuantity = value; suppliedFields.add("total_quantity"); }
    @JsonProperty("max_per_user") public void setMaxPerUser(Integer value) { maxPerUser = value; suppliedFields.add("max_per_user"); }
    @JsonProperty("sale_start_at") public void setSaleStartAt(LocalDateTime value) { saleStartAt = value; suppliedFields.add("sale_start_at"); }
    @JsonProperty("sale_end_at") public void setSaleEndAt(LocalDateTime value) { saleEndAt = value; suppliedFields.add("sale_end_at"); }
    public void setStatus(String value) { status = value; suppliedFields.add("status"); }

    @NotBlank
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    private String description;

    @DecimalMin(value = "0.0", message = "Price must be greater than or equal to 0")
    @Digits(integer = 10, fraction = 2, message = "Price must fit NUMERIC(12,2)")
    private BigDecimal price;

    @Min(value = 0, message = "Total quantity must not be negative")
    @JsonProperty("total_quantity")
    private Integer totalQuantity;

    @Min(value = 1, message = "Max per user must be greater than 0")
    @JsonProperty("max_per_user")
    private Integer maxPerUser;

    @JsonProperty("sale_start_at")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = Rfc3339UtcLocalDateTimeDeserializer.class)
    private LocalDateTime saleStartAt;

    @JsonProperty("sale_end_at")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = Rfc3339UtcLocalDateTimeDeserializer.class)
    private LocalDateTime saleEndAt;

    @jakarta.validation.constraints.Pattern(regexp = "DRAFT|ACTIVE|ON_SALE|SUSPENDED|CLOSED|SOLD_OUT")
    private String status;
}
