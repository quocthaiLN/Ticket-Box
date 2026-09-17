package com.ticketbox.api.module.catalog.controllers;

import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.catalog.domain.dtos.*;
import com.ticketbox.api.module.catalog.services.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/concerts")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService CatalogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConcertResponse>>> getConcerts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startsAt") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder) {

        Sort.Direction direction = sortOrder.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<ConcertResponse> result = CatalogService.getPublishedConcerts(q, city, from, to, pageable);

        ApiResponse.PaginationMeta paginationMeta = ApiResponse.PaginationMeta.builder()
                .page(result.getNumber())
                .pageSize(result.getSize())
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasMore(result.hasNext())
                .build();

        return ResponseEntity.ok(ApiResponse.success(result.getContent(), paginationMeta));
    }

    @GetMapping("/{concertId}")
    public ResponseEntity<ApiResponse<ConcertDetailResponse>> getConcertDetail(@PathVariable UUID concertId) {
        ConcertDetailResponse detail = CatalogService.getConcertDetail(concertId);
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping("/{concertId}/metadata")
    public ResponseEntity<ApiResponse<ConcertMetadataResponse>> getConcertMetadata(@PathVariable UUID concertId) {
        ConcertMetadataResponse metadata = CatalogService.getConcertMetadata(concertId);
        return ResponseEntity.ok(ApiResponse.success(metadata));
    }

    @GetMapping("/{concertId}/seat-map")
    public ResponseEntity<ApiResponse<SeatMapResponse>> getConcertSeatMap(@PathVariable UUID concertId) {
        SeatMapResponse seatMap = CatalogService.getConcertSeatMap(concertId);
        return ResponseEntity.ok(ApiResponse.success(seatMap));
    }

    @GetMapping("/{concertId}/ticket-types")
    public ResponseEntity<ApiResponse<List<TicketTypeResponse>>> getTicketTypes(
            @PathVariable UUID concertId,
            @RequestParam(defaultValue = "false") boolean includeClosed) {
        List<TicketTypeResponse> ticketTypes = CatalogService.getTicketTypes(concertId, includeClosed);
        return ResponseEntity.ok(ApiResponse.success(ticketTypes));
    }

    @GetMapping("/{concertId}/inventory")
    public ResponseEntity<ApiResponse<InventoryResponse>> getInventory(@PathVariable UUID concertId) {
        InventoryResponse inventory = CatalogService.getInventory(concertId);
        return ResponseEntity.ok(ApiResponse.success(inventory));
    }

    @GetMapping("/{concertId}/quota")
    public ResponseEntity<ApiResponse<ConcertQuotaResponse>> getQuota(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId) {
        ConcertQuotaResponse quota = CatalogService.getQuota(userDetails.getUser(), concertId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(quota));
    }

}
