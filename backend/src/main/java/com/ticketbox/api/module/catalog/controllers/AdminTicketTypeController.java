package com.ticketbox.api.module.catalog.controllers;

import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.catalog.domain.dtos.TicketTypeResponse;
import com.ticketbox.api.module.catalog.domain.dtos.UpdateTicketTypeRequest;
import com.ticketbox.api.module.catalog.services.AdminConcertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/ticket-types")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
@RequiredArgsConstructor
public class AdminTicketTypeController {

    private final AdminConcertService adminConcertService;

    @PatchMapping("/{ticketTypeId}")
    public ResponseEntity<ApiResponse<TicketTypeResponse>> updateTicketType(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID ticketTypeId,
            @Valid @RequestBody UpdateTicketTypeRequest request) {
        TicketTypeResponse ticketType = adminConcertService.updateTicketType(userDetails.getUser(), ticketTypeId, request);
        return ResponseEntity.ok(ApiResponse.success(ticketType));
    }
}
