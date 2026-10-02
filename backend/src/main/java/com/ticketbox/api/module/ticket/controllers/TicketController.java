package com.ticketbox.api.module.ticket.controllers;

import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.ticket.domain.dtos.TicketQrResponse;
import com.ticketbox.api.module.ticket.domain.dtos.TicketResponse;
import com.ticketbox.api.module.ticket.services.TicketService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/my-tickets")
@RequiredArgsConstructor
@Validated
public class TicketController {

        private final TicketService ticketService;

        @GetMapping
        @PreAuthorize("hasAnyRole('AUDIENCE', 'ADMIN')")
        public ResponseEntity<ApiResponse<List<TicketResponse>>> getMyTickets(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
                Page<TicketResponse> result = ticketService.getMyTickets(userDetails.getUser(),
                                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt")));
                ApiResponse.PaginationMeta pagination = ApiResponse.PaginationMeta.builder()
                                .page(result.getNumber())
                                .pageSize(result.getSize())
                                .totalItems(result.getTotalElements())
                                .totalPages(result.getTotalPages())
                                .hasMore(result.hasNext())
                                .build();
                return ResponseEntity.ok(ApiResponse.success(result.getContent(), pagination));
        }

        @GetMapping("/{ticketId}/qr")
        @PreAuthorize("hasAnyRole('AUDIENCE', 'ADMIN')")
        public ResponseEntity<ApiResponse<TicketQrResponse>> getMyTicketQr(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @PathVariable UUID ticketId) {
                TicketQrResponse response = ticketService.getMyTicketQr(userDetails.getUser(), ticketId);
                return ResponseEntity.ok()
                                .cacheControl(CacheControl.noStore().cachePrivate())
                                .body(ApiResponse.success(response));
        }
}