package com.ticketbox.api.module.catalog.controllers;

import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.catalog.domain.dtos.*;
import com.ticketbox.api.module.catalog.services.AdminConcertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin/concerts")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
@RequiredArgsConstructor
public class AdminConcertController {

    private final AdminConcertService adminConcertService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConcertDetailResponse>>> getConcerts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {

        Sort.Direction direction = sortOrder.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<ConcertDetailResponse> result = adminConcertService.getAdminConcerts(userDetails.getUser(), status, q, pageable);

        ApiResponse.PaginationMeta paginationMeta = ApiResponse.PaginationMeta.builder()
                .page(result.getNumber())
                .pageSize(result.getSize())
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasMore(result.hasNext())
                .build();

        return ResponseEntity.ok(ApiResponse.success(result.getContent(), paginationMeta));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConcertDetailResponse>> createConcert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateConcertRequest request) {
        ConcertDetailResponse concert = adminConcertService.createConcert(userDetails.getUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(concert));
    }

    @PatchMapping("/{concertId}")
    public ResponseEntity<ApiResponse<ConcertDetailResponse>> updateConcert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId,
            @Valid @RequestBody UpdateConcertRequest request) {
        ConcertDetailResponse concert = adminConcertService.updateConcert(userDetails.getUser(), concertId, request);
        return ResponseEntity.ok(ApiResponse.success(concert));
    }

    @PostMapping("/{concertId}/publish")
    public ResponseEntity<ApiResponse<ConcertDetailResponse>> publishConcert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId) {
        ConcertDetailResponse concert = adminConcertService.publishConcert(userDetails.getUser(), concertId);
        return ResponseEntity.ok(ApiResponse.success(concert));
    }

    @PostMapping("/{concertId}/cancel")
    public ResponseEntity<ApiResponse<ConcertDetailResponse>> cancelConcert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        ConcertDetailResponse concert = adminConcertService.cancelConcert(userDetails.getUser(), concertId, reason);
        return ResponseEntity.ok(ApiResponse.success(concert));
    }

    @PostMapping("/{concertId}/seat-zones")
    public ResponseEntity<ApiResponse<SeatZoneResponse>> createSeatZone(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId,
            @Valid @RequestBody CreateSeatZoneRequest request) {
        SeatZoneResponse seatZone = adminConcertService.createSeatZone(userDetails.getUser(), concertId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(seatZone));
    }

    @PostMapping("/{concertId}/ticket-types")
    public ResponseEntity<ApiResponse<TicketTypeResponse>> createTicketType(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId,
            @Valid @RequestBody CreateTicketTypeRequest request) {
        TicketTypeResponse ticketType = adminConcertService.createTicketType(userDetails.getUser(), concertId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(ticketType));
    }
}
