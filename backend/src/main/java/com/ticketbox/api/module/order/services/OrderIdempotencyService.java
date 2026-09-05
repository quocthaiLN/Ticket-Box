package com.ticketbox.api.module.order.services;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderItemRequest;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderRequest;
import com.ticketbox.api.module.order.domain.dtos.HeldOrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderIdempotencyService {

    private static final String KEY_PREFIX = "idempotency:hold:";
    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";
    private static final String SUCCESS = "SUCCESS";
    private static final String ERROR = "ERROR";
    private static final Duration TTL = OrderServiceImpl.HOLD_TTL;
    private static final int POLL_ATTEMPTS = 600;
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private static final DefaultRedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3]); return 1; end; return 0;",
            Long.class);
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]); end; return 0;",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final OrderService orderService;

    public ApiResponse<HeldOrderResponse> createOrReplay(User currentUser, UUID idempotencyKey,
                                                           CreateOrderRequest request) {
        String fingerprint = fingerprint(currentUser, request);
        String redisKey = KEY_PREFIX + idempotencyKey;
        String processingValue = processingValue(fingerprint);

        String cachedValue = getValue(redisKey);
        if (cachedValue != null) {
            return waitForCompletedResponse(redisKey, fingerprint);
        }

        ApiResponse<HeldOrderResponse> existingOrder = findExistingOrder(currentUser, idempotencyKey.toString(), request);
        if (existingOrder != null) {
            storeCompleted(redisKey, completedValue(fingerprint, existingOrder));
            return existingOrder;
        }

        if (!tryStart(redisKey, processingValue)) {
            return waitForCompletedResponse(redisKey, fingerprint);
        }

        try {
            HeldOrderResponse heldOrder = orderService.createHeldOrder(currentUser, idempotencyKey.toString(), request);
            ApiResponse<HeldOrderResponse> response = ApiResponse.success(heldOrder);
            storeCompletedIfProcessing(redisKey, processingValue, completedValue(fingerprint, response));
            return response;
        } catch (AppException exception) {
            storeCompletedIfProcessing(redisKey, processingValue, errorValue(fingerprint, exception));
            throw exception;
        } catch (RuntimeException exception) {
            removeProcessingMarker(redisKey, processingValue);
            throw exception;
        }
    }

    private ApiResponse<HeldOrderResponse> findExistingOrder(User currentUser, String idempotencyKey,
                                                               CreateOrderRequest request) {
        return orderService.findExistingOrder(currentUser, idempotencyKey, request)
                .map(ApiResponse::success)
                .orElse(null);
    }

    private boolean tryStart(String redisKey, String processingValue) {
        try {
            Boolean started = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, processingValue, TTL);
            if (started == null) {
                throw unavailable();
            }
            return started;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private ApiResponse<HeldOrderResponse> waitForCompletedResponse(String redisKey, String fingerprint) {
        for (int attempt = 0; attempt < POLL_ATTEMPTS; attempt++) {
            String value = getValue(redisKey);
            if (value == null) {
                throw unavailable();
            }

            JsonNode record = readRecord(value);
            validateFingerprint(record, fingerprint);
            if (COMPLETED.equals(record.path("state").asText())) {
                return readResponse(record);
            }

            try {
                Thread.sleep(POLL_INTERVAL);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw unavailable();
            }
        }
        throw unavailable();
    }

    private String getValue(String redisKey) {
        try {
            return stringRedisTemplate.opsForValue().get(redisKey);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private void storeCompleted(String redisKey, String value) {
        try {
            stringRedisTemplate.opsForValue().set(redisKey, value, TTL);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private void storeCompletedIfProcessing(String redisKey, String processingValue, String completedValue) {
        try {
            Long updated = stringRedisTemplate.execute(COMPARE_AND_SET, List.of(redisKey), processingValue,
                    completedValue, Long.toString(TTL.toSeconds()));
            if (!Long.valueOf(1).equals(updated)) {
                throw unavailable();
            }
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private void removeProcessingMarker(String redisKey, String processingValue) {
        try {
            stringRedisTemplate.execute(COMPARE_AND_DELETE, List.of(redisKey), processingValue);
        } catch (Exception ignored) {
            // A stale processing marker expires with the hold TTL; never delete another request's marker.
        }
    }

    private String processingValue(String fingerprint) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("state", PROCESSING);
        value.put("fingerprint", fingerprint);
        return writeRecord(value);
    }

    private String completedValue(String fingerprint, ApiResponse<HeldOrderResponse> response) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("state", COMPLETED);
        value.put("fingerprint", fingerprint);
        value.put("outcome", SUCCESS);
        value.set("response", objectMapper.valueToTree(response));
        return writeRecord(value);
    }

    private String errorValue(String fingerprint, AppException exception) {
        ObjectNode value = objectMapper.createObjectNode();
        value.put("state", COMPLETED);
        value.put("fingerprint", fingerprint);
        value.put("outcome", ERROR);
        value.put("http_status", exception.getStatus().value());
        value.put("error_code", exception.getErrorCode());
        value.put("message", exception.getMessage());
        if (exception.getDetails() != null) {
            value.set("details", objectMapper.valueToTree(exception.getDetails()));
        }
        return writeRecord(value);
    }

    private String writeRecord(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private JsonNode readRecord(String value) {
        try {
            JsonNode record = objectMapper.readTree(value);
            if (!record.hasNonNull("state") || !record.hasNonNull("fingerprint")) {
                throw unavailable();
            }
            return record;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private void validateFingerprint(JsonNode record, String fingerprint) {
        if (!fingerprint.equals(record.path("fingerprint").asText())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key was already used for a different request");
        }
    }

    private ApiResponse<HeldOrderResponse> readResponse(JsonNode record) {
        if (ERROR.equals(record.path("outcome").asText())) {
            throw new AppException(HttpStatus.valueOf(record.path("http_status").asInt()),
                    record.path("error_code").asText(), record.path("message").asText(),
                    record.has("details") ? objectMapper.convertValue(record.get("details"), Object.class) : null);
        }
        if (!SUCCESS.equals(record.path("outcome").asText()) || !record.hasNonNull("response")) {
            throw unavailable();
        }
        try {
            JavaType type = objectMapper.getTypeFactory()
                    .constructParametricType(ApiResponse.class, HeldOrderResponse.class);
            return objectMapper.treeToValue(record.get("response"), type);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private String fingerprint(User currentUser, CreateOrderRequest request) {
        String payload = currentUser.getId() + "|" + request.getConcertId() + "|"
                + request.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getTicketTypeId().toString()))
                .map(item -> item.getTicketTypeId() + ":" + item.getQuantity())
                .reduce("", (left, right) -> left + "|" + right);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private AppException unavailable() {
        return new AppException(HttpStatus.SERVICE_UNAVAILABLE, "IDEMPOTENCY_UNAVAILABLE",
                "Order idempotency service is unavailable; retry with the same Idempotency-Key");
    }
}
