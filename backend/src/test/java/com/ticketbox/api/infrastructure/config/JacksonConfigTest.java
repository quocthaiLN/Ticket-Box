package com.ticketbox.api.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.order.domain.dtos.OrderResponse;
import com.ticketbox.api.module.catalog.domain.dtos.CreateConcertRequest;
import com.ticketbox.api.module.catalog.domain.dtos.UpdateConcertRequest;
import com.ticketbox.api.module.catalog.domain.dtos.UpdateSeatZoneRequest;
import com.ticketbox.api.module.catalog.domain.dtos.UpdateTicketTypeRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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

    @Test
    void parsesAdminConcertTimesWithOffsetAndNormalizesToUtc() throws Exception {
        ObjectMapper objectMapper = new JacksonConfig().objectMapper();
        CreateConcertRequest request = objectMapper.readValue(
                "{\"starts_at\":\"2026-10-01T12:00:00+07:00\"}", CreateConcertRequest.class);

        assertEquals(LocalDateTime.of(2026, 10, 1, 5, 0), request.getStartsAt());
    }

    @Test
    void patchDtoTracksExplicitNullSeparatelyFromMissingField() throws Exception {
        ObjectMapper objectMapper = new JacksonConfig().objectMapper();
        UpdateConcertRequest request = objectMapper.readValue(
                "{\"description\":null,\"starts_at\":\"2026-10-01T12:00:00+07:00\"}", UpdateConcertRequest.class);

        assertEquals(true, request.getDescription().isPresent());
        org.junit.jupiter.api.Assertions.assertNull(request.getDescription().get());
        assertEquals(false, request.getArtistBio().isPresent());
        assertEquals(true, request.getStartsAt().isPresent());
        assertEquals(LocalDateTime.of(2026, 10, 1, 5, 0), request.getStartsAt().get());
    }

    @Test
    void seatZonePatchDtoTracksJsonNullable() throws Exception {
        ObjectMapper objectMapper = new JacksonConfig().objectMapper();
        UpdateSeatZoneRequest request = objectMapper.readValue(
                "{\"description\":null,\"capacity\":50}", UpdateSeatZoneRequest.class);

        assertEquals(true, request.getDescription().isPresent());
        org.junit.jupiter.api.Assertions.assertNull(request.getDescription().get());
        assertEquals(true, request.getCapacity().isPresent());
        assertEquals(50, request.getCapacity().get());
        assertEquals(false, request.getName().isPresent());
    }

    @Test
    void ticketTypePatchDtoTracksJsonNullable() throws Exception {
        ObjectMapper objectMapper = new JacksonConfig().objectMapper();
        UpdateTicketTypeRequest request = objectMapper.readValue(
                "{\"price\":150000.00,\"sale_start_at\":\"2026-10-01T12:00:00+07:00\"}", UpdateTicketTypeRequest.class);

        assertEquals(true, request.getPrice().isPresent());
        assertEquals(new BigDecimal("150000.00"), request.getPrice().get());
        assertEquals(true, request.getSaleStartAt().isPresent());
        assertEquals(LocalDateTime.of(2026, 10, 1, 5, 0), request.getSaleStartAt().get());
        assertEquals(false, request.getName().isPresent());
    }
}
