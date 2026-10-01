package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDateTime;

public class Rfc3339UtcLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        try {
            return OffsetDateTime.parse(parser.getValueAsString()).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (RuntimeException exception) {
            return (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, parser.getValueAsString(),
                    "Expected RFC 3339 datetime with Z or explicit offset");
        }
    }
}
