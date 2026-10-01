package com.ticketbox.api.module.catalog.domain.dtos;

import com.ticketbox.api.infrastructure.exception.AppException;
import org.springframework.http.HttpStatus;

final class UnknownAdminField {
    private UnknownAdminField() {}

    static void reject(String field) {
        throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_FIELD", "Unsupported request field: " + field);
    }
}
