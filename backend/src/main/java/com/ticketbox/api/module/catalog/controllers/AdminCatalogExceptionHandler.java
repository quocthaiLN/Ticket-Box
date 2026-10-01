package com.ticketbox.api.module.catalog.controllers;

import com.ticketbox.api.infrastructure.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import java.net.URI;
import java.util.UUID;

@RestControllerAdvice(assignableTypes = {ConcertController.class, SeatZoneController.class, TicketTypeController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminCatalogExceptionHandler {
    @ExceptionHandler(AppException.class)
    ResponseEntity<ProblemDetail> appException(AppException ex, HttpServletRequest request) {
        return problem(ex.getStatus().value(), ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        return problem(422, "VALIDATION_ERROR", "Request validation failed", request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> malformed(Exception ex, HttpServletRequest request) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
            if (cause instanceof AppException app) return problem(app.getStatus().value(), app.getErrorCode(), app.getMessage(), request);
        }
        return problem(400, "INVALID_REQUEST", "Malformed request", request);
    }

    private ResponseEntity<ProblemDetail> problem(int status, String code, String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        body.setTitle(code);
        body.setType(URI.create("https://api.ticketbox.vn/errors/" + code.toLowerCase().replace('_', '-')));
        body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty("code", code);
        body.setProperty("request_id", "req_" + UUID.randomUUID().toString().substring(0, 8));
        return ResponseEntity.status(status).body(body);
    }
}
