package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openapitools.jackson.nullable.JsonNullable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTicketTypeRequest {

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        UnknownAdminField.reject(field);
    }

    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Builder.Default
    private JsonNullable<String> name = JsonNullable.undefined();

    @Builder.Default
    private JsonNullable<String> description = JsonNullable.undefined();

    @DecimalMin(value = "0.0", message = "Price must be greater than or equal to 0")
    @Digits(integer = 10, fraction = 2, message = "Price must fit NUMERIC(12,2)")
    @Builder.Default
    private JsonNullable<BigDecimal> price = JsonNullable.undefined();

    @Min(value = 0, message = "Total quantity must not be negative")
    @JsonProperty("total_quantity")
    @Builder.Default
    private JsonNullable<Integer> totalQuantity = JsonNullable.undefined();

    @Min(value = 1, message = "Max per user must be greater than 0")
    @JsonProperty("max_per_user")
    @Builder.Default
    private JsonNullable<Integer> maxPerUser = JsonNullable.undefined();

    @JsonProperty("sale_start_at")
    @JsonDeserialize(contentUsing = Rfc3339UtcLocalDateTimeDeserializer.class)
    @Builder.Default
    private JsonNullable<LocalDateTime> saleStartAt = JsonNullable.undefined();

    @JsonProperty("sale_end_at")
    @JsonDeserialize(contentUsing = Rfc3339UtcLocalDateTimeDeserializer.class)
    @Builder.Default
    private JsonNullable<LocalDateTime> saleEndAt = JsonNullable.undefined();

    @Pattern(regexp = "DRAFT|ACTIVE|ON_SALE|SUSPENDED|CLOSED|SOLD_OUT")
    @Builder.Default
    private JsonNullable<String> status = JsonNullable.undefined();
}
