package com.ticketbox.api.infrastructure.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final String CORRELATION_ATTRIBUTE = "ticketbox.traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = (String) request.getAttribute(CORRELATION_ATTRIBUTE);
        if (traceId == null) {
            traceId = "req_" + UUID.randomUUID().toString().replace("-", "");
            request.setAttribute(CORRELATION_ATTRIBUTE, traceId);
        }

        String previousTraceId = MDC.get("traceId");
        MDC.put("traceId", traceId);
        response.setHeader("X-Request-Id", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousTraceId == null) MDC.remove("traceId");
            else MDC.put("traceId", previousTraceId);
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
