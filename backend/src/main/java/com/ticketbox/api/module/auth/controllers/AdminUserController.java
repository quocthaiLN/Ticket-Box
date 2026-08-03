package com.ticketbox.api.module.auth.controllers;

import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.domain.dtos.UpdateStatusRequest;
import com.ticketbox.api.module.auth.domain.dtos.UserResponse;
import com.ticketbox.api.module.auth.services.AdminUserService;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<UserResponse> result = adminUserService.getUsers(pageable);

        ApiResponse.PaginationMeta paginationMeta = ApiResponse.PaginationMeta.builder()
                .page(result.getNumber())
                .pageSize(result.getSize())
                .totalItems(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasMore(result.hasNext())
                .build();

        return ResponseEntity.ok(ApiResponse.success(result.getContent(), paginationMeta));
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails adminDetails,
            HttpServletRequest servletRequest) {

        UUID adminId = adminDetails != null ? adminDetails.getUser().getId() : null;
        String ipAddress = servletRequest.getRemoteAddr();
        String userAgent = servletRequest.getHeader("User-Agent");

        UserResponse response = adminUserService.updateUserStatus(userId, request.getStatus(), adminId, ipAddress, userAgent);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
