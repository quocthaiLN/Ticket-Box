package com.ticketbox.api.module.catalog.controllers;

import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.catalog.domain.dtos.SeatZoneResponse;
import com.ticketbox.api.module.catalog.domain.dtos.UpdateSeatZoneRequest;
import com.ticketbox.api.module.catalog.services.AdminConcertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/seat-zones")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
@RequiredArgsConstructor
public class AdminSeatZoneController {

    private final AdminConcertService adminConcertService;

    @PatchMapping("/{seatZoneId}")
    public ResponseEntity<ApiResponse<SeatZoneResponse>> updateSeatZone(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID seatZoneId,
            @Valid @RequestBody UpdateSeatZoneRequest request) {
        SeatZoneResponse seatZone = adminConcertService.updateSeatZone(userDetails.getUser(), seatZoneId, request);
        return ResponseEntity.ok(ApiResponse.success(seatZone));
    }
}
