package com.ticketbox.api.module.shared.idempotency;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.infrastructure.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ObjectMapper objectMapper;
    private final IdempotencyHelper idempotencyHelper;

    /**
     * Thực thi action theo cơ chế idempotency với kiểu response không generic.
     *
     * @param redisKey khóa Redis xác định request idempotent
     * @param fingerprint dấu vân tay của payload để chặn tái sử dụng cùng key cho payload khác
     * @param ttl thời gian sống của record idempotency trên Redis
     * @param responseType kiểu dữ liệu của response dùng để khôi phục kết quả đã cache
     * @param fallbackLookup truy vấn dự phòng, thường là từ database, khi Redis chưa có record; có thể là {@code null}
     * @param action tác vụ cần thực thi một lần khi chưa có kết quả trước đó
     * @param <T> kiểu dữ liệu response
     * @return response đã cache, được khôi phục từ fallback hoặc vừa tạo bởi action
     */
    public <T> T execute(
            String redisKey,
            String fingerprint,
            Duration ttl,
            Class<T> responseType,
            Supplier<Optional<T>> fallbackLookup,
            Supplier<T> action) {
        JavaType javaType = objectMapper.getTypeFactory().constructType(responseType);
        return execute(redisKey, fingerprint, ttl, javaType, fallbackLookup, action);
    }

    /**
     * Thực thi action theo cơ chế idempotency với kiểu response có thể generic.
     *
     * @param redisKey khóa Redis xác định request idempotent
     * @param fingerprint dấu vân tay của payload để chặn tái sử dụng cùng key cho payload khác
     * @param ttl thời gian sống của record idempotency trên Redis
     * @param responseType kiểu Jackson đầy đủ của response, hỗ trợ kiểu generic như {@code List<T>}
     * @param fallbackLookup truy vấn dự phòng, thường là từ database, khi Redis chưa có record; có thể là {@code null}
     * @param action tác vụ cần thực thi một lần khi chưa có kết quả trước đó
     * @param <T> kiểu dữ liệu response
     * @return response đã cache, được khôi phục từ fallback hoặc vừa tạo bởi action
     */
    public <T> T execute(
            String redisKey,
            String fingerprint,
            Duration ttl,
            JavaType responseType,
            Supplier<Optional<T>> fallbackLookup,
            Supplier<T> action) {
        String processingValue = idempotencyHelper.processingValue(fingerprint);

        String cachedValue = idempotencyHelper.getValue(redisKey);
        if (cachedValue != null) {
            return idempotencyHelper.waitForCompletedResponse(redisKey, fingerprint, responseType);
        }

        if (fallbackLookup != null) {
            Optional<T> fallbackResult = fallbackLookup.get();
            if (fallbackResult != null && fallbackResult.isPresent()) {
                T result = fallbackResult.get();
                idempotencyHelper.storeCompleted(redisKey, idempotencyHelper.completedValue(fingerprint, result), ttl);
                return result;
            }
        }

        if (!idempotencyHelper.tryStart(redisKey, processingValue, ttl)) {
            return idempotencyHelper.waitForCompletedResponse(redisKey, fingerprint, responseType);
        }

        try {
            T result = action.get();
            idempotencyHelper.storeCompletedIfProcessing(redisKey, processingValue,
                    idempotencyHelper.completedValue(fingerprint, result), ttl);
            return result;
        } catch (AppException exception) {
            idempotencyHelper.storeCompletedIfProcessing(redisKey, processingValue,
                    idempotencyHelper.errorValue(fingerprint, exception), ttl);
            throw exception;
        } catch (RuntimeException exception) {
            idempotencyHelper.removeProcessingMarker(redisKey, processingValue);
            throw exception;
        }
    }
}
