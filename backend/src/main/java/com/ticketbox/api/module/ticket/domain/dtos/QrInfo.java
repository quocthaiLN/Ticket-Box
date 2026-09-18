package com.ticketbox.api.module.ticket.domain.dtos;

public record QrInfo(
        boolean available,
        String displayUrl) {
}