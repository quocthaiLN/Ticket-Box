package com.ticketbox.api.module.shared.idempotency;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.infrastructure.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class IdempotencyHelper {

    private static final int POLL_ATTEMPTS = 600;
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final DefaultRedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3]); return 1; end; return 0;",
            Long.class);
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]); end; return 0;",
            Long.class);

    /**
     * Thử chiếm lock trên Redis bằng SETNX với trạng thái PROCESSING.
     * Trả về true nếu thành công, false nếu đã có request khác chiếm trước.
     */
    public boolean tryStart(String redisKey, String processingValue, Duration ttl) {
        try {
            Boolean started = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, processingValue, ttl);
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

    /**
     * Polling Redis định kỳ (tối đa 30s) chờ request đang chạy chuyển sang
     * COMPLETED.
     */
    public <T> T waitForCompletedResponse(String redisKey, String fingerprint, JavaType responseType) {
        for (int attempt = 0; attempt < POLL_ATTEMPTS; attempt++) {
            String value = getValue(redisKey);
            if (value == null) {
                throw unavailable();
            }

            IdempotencyRecord record = readRecord(value);
            validateFingerprint(record, fingerprint);
            IdempotencyState state = record.state();
            if (state == IdempotencyState.COMPLETED) {
                return readResponse(record, responseType);
            }

            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw unavailable();
            }
        }
        throw unavailable();
    }

    /**
     * Đọc giá trị raw JSON từ Redis theo key.
     */
    public String getValue(String redisKey) {
        try {
            return stringRedisTemplate.opsForValue().get(redisKey);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    /**
     * Lưu trực tiếp kết quả COMPLETED vào Redis (dùng khi khôi phục từ DB
     * fallbackLookup).
     */
    public void storeCompleted(String redisKey, String value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(redisKey, value, ttl);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    /**
     * Dùng Lua script COMPARE_AND_SET để cập nhật PROCESSING -> COMPLETED an toàn.
     */
    public void storeCompletedIfProcessing(String redisKey, String processingValue, String completedValue,
            Duration ttl) {
        try {
            Long updated = stringRedisTemplate.execute(COMPARE_AND_SET, List.of(redisKey), processingValue,
                    completedValue, Long.toString(ttl.toSeconds()));
            if (!Long.valueOf(1).equals(updated)) {
                throw unavailable();
            }
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    /**
     * Dùng Lua script COMPARE_AND_DELETE để xóa key PROCESSING khi gặp
     * RuntimeException bất ngờ, giải phóng lock.
     */
    public void removeProcessingMarker(String redisKey, String processingValue) {
        try {
            stringRedisTemplate.execute(COMPARE_AND_DELETE, List.of(redisKey), processingValue);
        } catch (Exception ignored) {
            // A stale processing marker expires with the TTL; never delete another
            // request's marker.
        }
    }

    /**
     * Tạo JSON record cho trạng thái PROCESSING.
     */
    public String processingValue(String fingerprint) {
        IdempotencyState state = IdempotencyState.PROCESSING;
        IdempotencyRecord record = new IdempotencyRecord(state, fingerprint, null, null, null, null, null,
                null);
        return writeRecord(record);
    }

    /**
     * Tạo JSON record cho kết quả COMPLETED thành công (outcome: SUCCESS).
     */
    public String completedValue(String fingerprint, Object result) {
        IdempotencyState state = IdempotencyState.COMPLETED;
        IdempotencyOutcome outcome = IdempotencyOutcome.SUCCESS;
        IdempotencyRecord record = new IdempotencyRecord(state, fingerprint, outcome,
                objectMapper.valueToTree(result), null, null, null, null);
        return writeRecord(record);
    }

    /**
     * Tạo JSON record cho kết quả COMPLETED thất bại do AppException (outcome:
     * ERROR).
     */
    public String errorValue(String fingerprint, AppException exception) {
        IdempotencyState state = IdempotencyState.COMPLETED;
        IdempotencyOutcome outcome = IdempotencyOutcome.ERROR;
        JsonNode details = exception.getDetails() == null ? null : objectMapper.valueToTree(exception.getDetails());
        IdempotencyRecord record = new IdempotencyRecord(state, fingerprint, outcome, null,
                exception.getStatus().value(), exception.getErrorCode(), exception.getMessage(), details);
        return writeRecord(record);
    }

    /**
     * Serialize IdempotencyRecord thành chuỗi String JSON.
     */
    private String writeRecord(IdempotencyRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    /**
     * Parse chuỗi JSON thành IdempotencyRecord và validate các trường bắt buộc
     * (state,
     * fingerprint).
     */
    private IdempotencyRecord readRecord(String value) {
        try {
            IdempotencyRecord record = objectMapper.readValue(value, IdempotencyRecord.class);
            if (record.state() == null || record.fingerprint() == null) {
                throw unavailable();
            }
            return record;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    /**
     * Kiểm tra tính toàn vẹn của request payload (ngăn chặn tái sử dụng cùng key
     * cho payload khác).
     */
    private void validateFingerprint(IdempotencyRecord record, String fingerprint) {
        if (!fingerprint.equals(record.fingerprint())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key was already used for a different request");
        }
    }

    /**
     * Trích xuất kết quả từ JSON record: throw lại AppException nếu outcome=ERROR
     * hoặc parse response nếu outcome=SUCCESS.
     */
    private <T> T readResponse(IdempotencyRecord record, JavaType responseType) {
        IdempotencyOutcome outcome = record.outcome();
        if (outcome == IdempotencyOutcome.ERROR) {
            if (record.httpStatus() == null || record.errorCode() == null || record.message() == null) {
                throw unavailable();
            }
            throw new AppException(HttpStatus.valueOf(record.httpStatus()), record.errorCode(), record.message(),
                    record.details() == null ? null : objectMapper.convertValue(record.details(), Object.class));
        }
        if (outcome != IdempotencyOutcome.SUCCESS || record.response() == null) {
            throw unavailable();
        }
        try {
            return objectMapper.treeToValue(record.response(), responseType);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    /**
     * Ném ngoại lệ HTTP 503 SERVICE_UNAVAILABLE khi Redis gặp sự cố hoặc timeout
     * khi chờ kết quả.
     */
    private AppException unavailable() {
        return new AppException(HttpStatus.SERVICE_UNAVAILABLE, "IDEMPOTENCY_UNAVAILABLE",
                "Idempotency service is unavailable; retry with the same Idempotency-Key");
    }
}
