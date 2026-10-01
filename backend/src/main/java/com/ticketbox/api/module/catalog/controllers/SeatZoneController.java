package com.ticketbox.api.module.catalog.controllers;

import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.catalog.domain.dtos.AdminSeatZoneResponse;
import com.ticketbox.api.module.catalog.domain.dtos.UpdateSeatZoneRequest;
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
@RequestMapping("/admin/seat-zones")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
@RequiredArgsConstructor
public class SeatZoneController {

    private final ConcertService concertService;

    @PatchMapping("/{seatZoneId}")
    public ResponseEntity<ApiResponse<AdminSeatZoneResponse>> updateSeatZone(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID seatZoneId,
            @Valid @RequestBody UpdateSeatZoneRequest request) {
        AdminSeatZoneResponse seatZone = concertService.updateSeatZone(userDetails.getUser(), seatZoneId, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(seatZone));
    }
}
