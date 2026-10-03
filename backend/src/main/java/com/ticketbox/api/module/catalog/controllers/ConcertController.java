package com.ticketbox.api.module.catalog.controllers;

import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.artistbio.services.ArtistBioService;
import com.ticketbox.api.module.artistbio.domain.dtos.ArtistBioJobResponse;
import org.springframework.web.multipart.MultipartFile;
import java.net.URI;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.catalog.domain.dtos.*;
import com.ticketbox.api.module.catalog.services.ConcertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import com.ticketbox.api.module.shared.validation.RequestValidationException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.Set;

@RestController
@RequestMapping("/admin/concerts")
@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;
    private final ArtistBioService artistBioService;

    @PostMapping(value = "/{concertId}/artist-bio-jobs", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ArtistBioJobResponse>> uploadArtistBio(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID concertId,
            @RequestPart("file") MultipartFile file) {
        var job = artistBioService.upload(userDetails.getUser(), concertId, file);
        return ResponseEntity.accepted().location(URI.create(
                "/admin/concerts/" + concertId + "/artist-bio-jobs/" + job.id()))
                .cacheControl(CacheControl.noStore()).body(ApiResponse.success(job));
    }

    @GetMapping("/{concertId}/artist-bio-jobs/{jobId}")
    public ResponseEntity<ApiResponse<ArtistBioJobResponse>> getArtistBioJob(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId, @PathVariable UUID jobId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(artistBioService.get(userDetails.getUser(), concertId, jobId)));
    }


    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminConcertResponse>>> getConcerts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam java.util.Map<String, String> allParams) {

        if (!Set.of("status", "q", "page", "size", "sortBy", "sortOrder").containsAll(allParams.keySet())) {
            throw new RequestValidationException("INVALID_QUERY", "Unsupported query parameter");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new RequestValidationException("INVALID_QUERY",
                    "page must be >= 0 and size must be between 1 and 100");
        }
        if (!Set.of("createdAt", "startsAt", "title").contains(sortBy)) {
            throw new RequestValidationException("INVALID_SORT", "Unsupported sortBy field");
        }
        if (!sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc")) {
            throw new RequestValidationException("INVALID_SORT", "sortOrder must be asc or desc");
        }

        Sort.Direction direction = sortOrder.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy).and(Sort.by(Sort.Direction.ASC, "id")));

        Page<AdminConcertResponse> result = concertService.getConcerts(userDetails.getUser(), status, q, pageable);

        ApiResponse.PaginationMeta paginationMeta = ApiResponse.PaginationMeta.builder()
                .page(result.getNumber())
                .pageSize(result.getSize())
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasMore(result.hasNext())
                .build();

        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(result.getContent(), paginationMeta));
    }

    @GetMapping("/{concertId}/metadata")
    public ResponseEntity<ApiResponse<AdminConcertMetadataResponse>> getConcertMetadata(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(concertService.getConcertMetadata(userDetails.getUser(), concertId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminConcertResponse>> createConcert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateConcertRequest request) {
        AdminConcertResponse concert = concertService.createConcert(userDetails.getUser(), request);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(concert));
    }

    @PatchMapping("/{concertId}")
    public ResponseEntity<ApiResponse<AdminConcertResponse>> updateConcert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId,
            @Valid @RequestBody UpdateConcertRequest request) {
        AdminConcertResponse concert = concertService.updateConcert(userDetails.getUser(), concertId, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(concert));
    }

    @PostMapping("/{concertId}/publish")
    public ResponseEntity<ApiResponse<AdminConcertResponse>> publishConcert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId) {
        AdminConcertResponse concert = concertService.publishConcert(userDetails.getUser(), concertId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(concert));
    }

    @PostMapping("/{concertId}/cancel")
    public ResponseEntity<ApiResponse<AdminConcertResponse>> cancelConcert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId,
            @RequestBody(required = false) CancelConcertRequest body) {
        String reason = body != null ? body.reason() : null;
        AdminConcertResponse concert = concertService.cancelConcert(userDetails.getUser(), concertId, reason);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(concert));
    }

    @PostMapping("/{concertId}/seat-zones")
    public ResponseEntity<ApiResponse<AdminSeatZoneResponse>> createSeatZone(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId,
            @Valid @RequestBody CreateSeatZoneRequest request) {
        AdminSeatZoneResponse seatZone = concertService.createSeatZone(userDetails.getUser(), concertId, request);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(seatZone));
    }

    @PostMapping("/{concertId}/ticket-types")
    public ResponseEntity<ApiResponse<AdminTicketTypeResponse>> createTicketType(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID concertId,
            @Valid @RequestBody CreateTicketTypeRequest request) {
        AdminTicketTypeResponse ticketType = concertService.createTicketType(userDetails.getUser(), concertId, request);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(ticketType));
    }
}
