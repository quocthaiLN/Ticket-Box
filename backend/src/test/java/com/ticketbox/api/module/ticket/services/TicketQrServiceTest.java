package com.ticketbox.api.module.ticket.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketQrServiceTest {

    private final TicketQrService qrService = new TicketQrService(
            "this-is-a-test-only-ticket-qr-secret-key-32-chars");

    @Test
    void issuesAStableSignatureForTheTicketId() {
        UUID ticketId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        TicketQrService.IssuedQr qr = qrService.issue(ticketId);

        assertThat(qr.payload()).isEqualTo("{\"ticket_id\":\"" + ticketId + "\"}");
        assertThat(qr.tokenHash()).hasSize(64);
        assertThat(qrService.hasValidSignature(ticketId, qr.signature())).isTrue();
    }

    @Test
    void rejectsASignatureForAnotherTicket() {
        TicketQrService.IssuedQr qr = qrService.issue(UUID.randomUUID());

        assertThat(qrService.hasValidSignature(UUID.randomUUID(), qr.signature())).isFalse();
    }
}
