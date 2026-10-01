package com.ticketbox.api.module.catalog.controllers;

import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.catalog.domain.dtos.AdminTicketTypeResponse;
import com.ticketbox.api.module.catalog.domain.dtos.UpdateTicketTypeRequest;
import com.ticketbox.api.module.catalog.services.ConcertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/ticket-types")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
@RequiredArgsConstructor
public class TicketTypeController {

    private final ConcertService concertService;

    @PatchMapping("/{ticketTypeId}")
    public ResponseEntity<ApiResponse<AdminTicketTypeResponse>> updateTicketType(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID ticketTypeId,
            @Valid @RequestBody UpdateTicketTypeRequest request) {
        AdminTicketTypeResponse ticketType = concertService.updateTicketType(userDetails.getUser(), ticketTypeId, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(ticketType));
    }
}
