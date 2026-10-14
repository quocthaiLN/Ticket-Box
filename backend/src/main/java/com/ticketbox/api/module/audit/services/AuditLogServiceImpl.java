package com.ticketbox.api.module.audit.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.audit.domain.dtos.ActorDto;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogDetailResponse;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogResponse;
import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.domain.entities.AuditLog;
import com.ticketbox.api.module.audit.domain.exception.AuditLogNotFoundException;
import com.ticketbox.api.module.audit.repositories.AuditLogRepository;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import com.ticketbox.api.module.audit.domain.mappers.AuditLogMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogMapper auditLogMapper;

    private static final Set<String> SENSITIVE_KEY_WORDS = Set.of(
            "password", "secret", "token", "accesstoken", "refreshtoken",
            "credential", "pin", "card", "cardnumber", "cvv", "otp"
    );

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void logBusinessSuccess(AuditAction action, String entityType, String entityId, Map<String, Object> metadata) {
        User actor = resolveCurrentActor();
        String ipAddress = resolveClientIp();
        String userAgent = resolveUserAgent();
        String metadataJson = serializeAndSanitizeMetadata(metadata);

        AuditLog auditLog = AuditLog.builder()
                .actor(actor)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadataJson)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        // Không bắt lỗi: Nếu DB ghi thất bại, exception ném ra để transaction cha rollback
        auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInvestigativeFailure(AuditAction action, String entityType, String entityId, Map<String, Object> metadata) {
        try {
            User actor = resolveCurrentActor();
            String ipAddress = resolveClientIp();
            String userAgent = resolveUserAgent();
            String metadataJson = serializeAndSanitizeMetadata(metadata);

            AuditLog auditLog = AuditLog.builder()
                    .actor(actor)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .metadata(metadataJson)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Nuốt lỗi DB audit và ghi log hệ thống để không làm che giấu exception ban đầu
            log.error("Failed to record investigative audit log: action={}, entityType={}, entityId={}, error={}",
                    action, entityType, entityId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void logAction(User actor, String action, String entityType, String entityId,
                          Map<String, Object> metadata, String ipAddress, String userAgent) {
        AuditAction auditAction;
        try {
            auditAction = action != null ? AuditAction.valueOf(action.trim().toUpperCase(Locale.ROOT)) : AuditAction.UPDATE_USER_STATUS;
        } catch (IllegalArgumentException e) {
            auditAction = AuditAction.UPDATE_USER_STATUS;
        }

        User effectiveActor = actor != null ? actor : resolveCurrentActor();
        String effectiveIp = ipAddress != null ? ipAddress : resolveClientIp();
        String effectiveUa = userAgent != null ? userAgent : resolveUserAgent();
        String metadataJson = serializeAndSanitizeMetadata(metadata);

        AuditLog auditLog = AuditLog.builder()
                .actor(effectiveActor)
                .action(auditAction)
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadataJson)
                .ipAddress(effectiveIp)
                .userAgent(effectiveUa)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(AuditAction action, UUID actorUserId, String entityType, String entityId,
                                                LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Pageable effectivePageable = ensureSort(pageable);
        Specification<AuditLog> spec = buildSpecification(action, actorUserId, entityType, entityId, from, to);
        return auditLogRepository.findAll(spec, effectivePageable).map(auditLogMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditLogDetailResponse getAuditLogById(UUID id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new AuditLogNotFoundException(id));
        return auditLogMapper.toDetailResponse(auditLog);
    }

    private User resolveCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUser();
        }
        return null;
    }

    private String resolveClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String firstIp = xForwardedFor.split(",")[0].trim();
                if (!firstIp.isEmpty()) {
                    return truncate(firstIp, 45);
                }
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return truncate(xRealIp.trim(), 45);
            }
            String remoteAddr = request.getRemoteAddr();
            if (remoteAddr != null) {
                return truncate(remoteAddr.trim(), 45);
            }
        }
        return null;
    }

    private String resolveUserAgent() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest().getHeader("User-Agent");
        }
        return null;
    }

    private String serializeAndSanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> sanitized = sanitizeMap(metadata);
        try {
            return objectMapper.writeValueAsString(sanitized);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit log metadata to JSON", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMap(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (isSensitiveKey(key)) {
                result.put(key, "***REDACTED***");
            } else if (value instanceof Map<?, ?> nestedMap) {
                result.put(key, sanitizeMap((Map<String, Object>) nestedMap));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
        for (String sensitive : SENSITIVE_KEY_WORDS) {
            if (normalized.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return null;
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private Pageable ensureSort(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return pageable;
    }

    private Specification<AuditLog> buildSpecification(AuditAction action, UUID actorUserId, String entityType,
                                                       String entityId, LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (actorUserId != null) {
                predicates.add(cb.equal(root.get("actor").get("id"), actorUserId));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(cb.equal(root.get("entityType"), entityType.trim()));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), entityId.trim()));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
