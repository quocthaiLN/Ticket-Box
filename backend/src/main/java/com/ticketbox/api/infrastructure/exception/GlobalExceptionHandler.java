package com.ticketbox.api.infrastructure.exception;

import com.ticketbox.api.infrastructure.response.ApiProblemFactory;
import com.ticketbox.api.module.shared.exception.BusinessException;
import com.ticketbox.api.module.shared.validation.RequestValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(BusinessException exception,
                                                           HttpServletRequest request) {
        String code = exception.getErrorCode().code();
        log.warn("Business error [{}] traceId={}: {}", code,
                ApiProblemFactory.correlationId(request), exception.getMessage());
        ProblemDetail problem = ApiProblemFactory.business(exception, request);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    /** Temporary HTTP adapter while non-catalog modules are migrated from AppException. */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<Object> handleLegacyAppException(AppException exception,
                                                             HttpServletRequest request) {
        if (exception.getStatus().is5xxServerError()) {
            log.error("Legacy technical error [{}] traceId={}", exception.getErrorCode(),
                    ApiProblemFactory.correlationId(request), exception);
        } else {
            log.warn("Legacy application error [{}] traceId={}: {}", exception.getErrorCode(),
                    ApiProblemFactory.correlationId(request), exception.getMessage());
        }
        ProblemDetail problem = ApiProblemFactory.legacy(exception, request);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<Object> handleRequestValidation(RequestValidationException exception,
                                                            HttpServletRequest request) {
        log.debug("Request validation failed [{}] traceId={}", exception.getCode(),
                ApiProblemFactory.correlationId(request));
        ProblemDetail problem = ApiProblemFactory.create(HttpStatus.BAD_REQUEST,
                exception.getCode(), exception.getMessage(), exception.getDetails(), request);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(BadCredentialsException exception,
                                                        HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Invalid email or password", Map.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException exception,
                                                      HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "Access denied: insufficient permissions", Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception traceId={}", ApiProblemFactory.correlationId(request), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected server error occurred", Map.of(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = new ArrayList<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            Map<String, String> item = new LinkedHashMap<>();
            if (error instanceof FieldError fieldError) item.put("field", fieldError.getField());
            item.put("code", error.getCode() == null ? "Invalid" : error.getCode());
            item.put("message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
            errors.add(Map.copyOf(item));
        });
        return validationResponse(errors, request, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (exception.isForReturnValue()) {
            log.error("Controller return value validation failed traceId={}", correlationId(request), exception);
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                    "An unexpected server error occurred", Map.of(), request, headers);
        }
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed",
                Map.of(), request, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        RequestValidationException validation = findValidationException(exception);
        if (validation != null) {
            ProblemDetail problem = ApiProblemFactory.create(HttpStatus.BAD_REQUEST,
                    validation.getCode(), validation.getMessage(), validation.getDetails(), servletRequest(request));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(problem);
        }
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Malformed request",
                Map.of(), request, headers);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(org.springframework.beans.TypeMismatchException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request contains an invalid value",
                Map.of(), request, headers);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            String code = frameworkCode(status.value());
            problem.setStatus(status.value());
            problem.setTitle(code);
            problem.setDetail(frameworkDetail(status.value()));
            problem.setType(java.net.URI.create("https://api.ticketbox.vn/errors/"
                    + code.toLowerCase().replace('_', '-')));
            HttpServletRequest servletRequest = servletRequest(request);
            if (servletRequest != null) problem.setInstance(java.net.URI.create(servletRequest.getRequestURI()));
            problem.setProperty("code", code);
            problem.setProperty("details", Map.of());
            String traceId = correlationId(request);
            problem.setProperty("traceId", traceId);
            problem.setProperty("request_id", traceId);
        }
        return super.createResponseEntity(body, headers, status, request);
    }

    private ResponseEntity<Object> validationResponse(List<Map<String, String>> errors,
            WebRequest request, HttpHeaders headers) {
        ProblemDetail problem = ApiProblemFactory.create(HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR", "Request validation failed", Map.of(), servletRequest(request));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    private ResponseEntity<Object> problem(HttpStatus status, String code, String detail,
            Map<String, Object> details, HttpServletRequest request) {
        ProblemDetail body = ApiProblemFactory.create(status, code, detail, details, request);
        return ResponseEntity.status(status).body(body);
    }

    private ResponseEntity<Object> problem(HttpStatus status, String code, String detail,
            Map<String, Object> details, WebRequest request, HttpHeaders headers) {
        ProblemDetail body = ApiProblemFactory.create(status, code, detail, details, servletRequest(request));
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    private String correlationId(WebRequest request) {
        return ApiProblemFactory.correlationId(servletRequest(request));
    }

    private HttpServletRequest servletRequest(WebRequest request) {
        return request instanceof ServletWebRequest servletWebRequest ? servletWebRequest.getRequest() : null;
    }

    private RequestValidationException findValidationException(Throwable exception) {
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            if (current instanceof RequestValidationException validation) return validation;
            if (current.getCause() == current) break;
        }
        return null;
    }

    private String frameworkCode(int status) {
        return switch (status) {
            case 400 -> "INVALID_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "ROUTE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 429 -> "RATE_LIMITED";
            default -> status >= 500 ? "INTERNAL_SERVER_ERROR" : "HTTP_ERROR";
        };
    }

    private String frameworkDetail(int status) {
        return switch (status) {
            case 400 -> "Malformed request";
            case 401 -> "Authentication is required to access this resource";
            case 403 -> "Access denied: insufficient permissions";
            case 404 -> "Resource not found";
            case 405 -> "HTTP method is not supported for this resource";
            case 406 -> "The requested response format is not available";
            case 413 -> "Request payload is too large";
            case 415 -> "Request media type is not supported";
            default -> status >= 500 ? "An unexpected server error occurred" : "Request could not be processed";
        };
    }
}
