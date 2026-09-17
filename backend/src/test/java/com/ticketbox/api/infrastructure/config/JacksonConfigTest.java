package com.ticketbox.api.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.order.domain.dtos.OrderResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JacksonConfigTest {

    @Test
    void serializesOrderHoldTimesAsIso8601Strings() throws Exception {
        ObjectMapper objectMapper = new JacksonConfig().objectMapper();
        LocalDateTime holdExpiresAt = LocalDateTime.of(2026, 9, 11, 20, 30, 0);

        String json = objectMapper.writeValueAsString(OrderResponse.builder()
                .holdExpiresAt(holdExpiresAt)
                .createdAt(holdExpiresAt.minusMinutes(15))
                .build());

        assertEquals("2026-09-11T20:30:00", objectMapper.readTree(json)
                .get("hold_expires_at").asText());
        assertEquals("2026-09-11T20:15:00", objectMapper.readTree(json)
                .get("created_at").asText());
    }
}
