package com.ticketbox.api.infrastructure.response;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ApiProblemFactory {
    private static final String CORRELATION_ATTRIBUTE = "ticketbox.traceId";

    private ApiProblemFactory() {}

    public static ProblemDetail business(BusinessException exception, HttpServletRequest request) {
        HttpStatus status = switch (exception.getErrorCode().type()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
        };
        return create(status, exception.getErrorCode().code(), exception.getMessage(),
                exception.getDetails(), request);
    }

    public static ProblemDetail legacy(AppException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatus().value());
        Map<String, Object> details = exception.getDetails() instanceof Map<?, ?> map
                ? stringKeyedMap(map) : Map.of();
        return create(status, exception.getErrorCode(), exception.getMessage(), details, request);
    }

    public static ProblemDetail create(HttpStatus status, String code, String detail,
                                       Map<String, Object> details, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(code);
        problem.setType(URI.create("https://api.ticketbox.vn/errors/"
                + code.toLowerCase().replace('_', '-')));
        if (request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        problem.setProperty("code", code);
        problem.setProperty("details", details == null ? Map.of() : details);
        String traceId = correlationId(request);
        problem.setProperty("traceId", traceId);
        problem.setProperty("request_id", traceId);
        return problem;
    }

    public static String correlationId(HttpServletRequest request) {
        Object attribute = request == null ? null : request.getAttribute(CORRELATION_ATTRIBUTE);
        if (attribute instanceof String value) return value;
        String mdcValue = MDC.get("traceId");
        if (mdcValue != null) return mdcValue;
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static Map<String, Object> stringKeyedMap(Map<?, ?> source) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key instanceof String stringKey && value != null) result.put(stringKey, value);
        });
        return Map.copyOf(result);
    }
}
