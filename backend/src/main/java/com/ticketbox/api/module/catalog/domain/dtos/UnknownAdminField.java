package com.ticketbox.api.module.catalog.domain.dtos;

import com.ticketbox.api.module.shared.validation.RequestValidationException;

final class UnknownAdminField {
    private UnknownAdminField() {}

    static void reject(String field) {
        throw new RequestValidationException("INVALID_REQUEST_FIELD", "Unsupported request field: " + field);
    }
}
