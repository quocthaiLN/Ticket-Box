package com.ticketbox.api.module.catalog.domain.dtos;

import com.fasterxml.jackson.annotation.JsonAnySetter;

public record CancelConcertRequest(String reason) {
    @JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        UnknownAdminField.reject(field);
    }
}
