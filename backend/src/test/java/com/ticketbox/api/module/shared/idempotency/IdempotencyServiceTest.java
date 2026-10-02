package com.ticketbox.api.module.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.order.domain.exception.OrderNotSettlableException;
import com.ticketbox.api.module.shared.exception.ErrorType;
import com.ticketbox.api.module.shared.exception.ReplayedBusinessException;
import com.ticketbox.api.module.shared.idempotency.exception.IdempotencyKeyReusedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final String REDIS_KEY = "idempotency:test:123";
    private static final String FINGERPRINT = "test-fingerprint-abc";
    private static final Duration TTL = Duration.ofMinutes(15);

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private IdempotencyService idempotencyService;

    record TestResult(String status, int count) {}

    @BeforeEach
    void setUp() {
        IdempotencyHelper idempotencyHelper = new IdempotencyHelper(stringRedisTemplate, objectMapper);
        idempotencyService = new IdempotencyService(objectMapper, idempotencyHelper);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Replays cached completed response without executing action or fallback")
    void execute_replaysCachedResponse() throws Exception {
        when(valueOperations.get(REDIS_KEY)).thenReturn(completedSuccessRecord(new TestResult("OK", 42)));

        AtomicBoolean actionExecuted = new AtomicBoolean(false);
        TestResult result = idempotencyService.execute(
                REDIS_KEY,
                FINGERPRINT,
                TTL,
                TestResult.class,
                () -> Optional.empty(),
                () -> {
                    actionExecuted.set(true);
                    return new TestResult("NEW", 0);
                });

        assertEquals("OK", result.status());
        assertEquals(42, result.count());
        org.junit.jupiter.api.Assertions.assertFalse(actionExecuted.get());
    }

    @Test
    @DisplayName("Rejects a different fingerprint that reuses an existing idempotency record")
    void execute_rejectsDifferentFingerprint() throws Exception {
        when(valueOperations.get(REDIS_KEY)).thenReturn(completedSuccessRecord(new TestResult("OK", 42)));

        IdempotencyKeyReusedException exception = assertThrows(IdempotencyKeyReusedException.class, () ->
                idempotencyService.execute(
                        REDIS_KEY,
                        "different-fingerprint",
                        TTL,
                        TestResult.class,
                        () -> Optional.empty(),
                        () -> new TestResult("NEW", 0)
                ));

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getErrorCode().code());
        assertEquals(ErrorType.CONFLICT, exception.getErrorCode().type());
    }

    @Test
    @DisplayName("Uses fallback lookup when not in cache, saves to Redis and returns result")
    void execute_usesFallbackLookupWhenPresent() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);

        AtomicBoolean actionExecuted = new AtomicBoolean(false);
        TestResult result = idempotencyService.execute(
                REDIS_KEY,
                FINGERPRINT,
                TTL,
                TestResult.class,
                () -> Optional.of(new TestResult("FROM_DB", 99)),
                () -> {
                    actionExecuted.set(true);
                    return new TestResult("NEW", 0);
                });

        assertEquals("FROM_DB", result.status());
        assertEquals(99, result.count());
        org.junit.jupiter.api.Assertions.assertFalse(actionExecuted.get());
        verify(valueOperations).set(eq(REDIS_KEY), anyString(), eq(TTL));
    }

    @Test
    @DisplayName("Executes action when key is new and acquires lock, then stores completed response")
    void execute_executesActionAndStoresCompleted() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).thenReturn(true);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(1L);

        AtomicBoolean actionExecuted = new AtomicBoolean(false);
        TestResult result = idempotencyService.execute(
                REDIS_KEY,
                FINGERPRINT,
                TTL,
                TestResult.class,
                () -> Optional.empty(),
                () -> {
                    actionExecuted.set(true);
                    return new TestResult("CREATED", 1);
                });

        assertEquals("CREATED", result.status());
        assertEquals(1, result.count());
        assertTrue(actionExecuted.get());
        verify(stringRedisTemplate).execute(any(DefaultRedisScript.class), eq(List.of(REDIS_KEY)), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Caches AppException when action fails and re-throws the exception")
    void execute_cachesAppExceptionAndRethrows() {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).thenReturn(true);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(1L);

        AppException thrown = assertThrows(AppException.class, () ->
                idempotencyService.execute(
                        REDIS_KEY,
                        FINGERPRINT,
                        TTL,
                        TestResult.class,
                        () -> Optional.empty(),
                        () -> {
                            throw new AppException(HttpStatus.CONFLICT, "PER_USER_LIMIT_EXCEEDED", "Limit exceeded");
                        }
                ));

        assertEquals("PER_USER_LIMIT_EXCEEDED", thrown.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, thrown.getStatus());
        verify(stringRedisTemplate).execute(any(DefaultRedisScript.class), eq(List.of(REDIS_KEY)), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Caches and replays a typed business exception without storing HTTP status")
    void execute_cachesAndReplaysBusinessException() throws Exception {
        when(valueOperations.get(REDIS_KEY)).thenReturn(null);
        when(valueOperations.setIfAbsent(eq(REDIS_KEY), anyString(), eq(TTL))).thenReturn(true);
        when(stringRedisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(1L);

        OrderNotSettlableException thrown = assertThrows(OrderNotSettlableException.class, () ->
                idempotencyService.execute(REDIS_KEY, FINGERPRINT, TTL, TestResult.class,
                        () -> Optional.empty(), () -> { throw new OrderNotSettlableException(); }));

        assertEquals("ORDER_NOT_SETTLABLE", thrown.getErrorCode().code());
        org.mockito.ArgumentCaptor<String> storedValue = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).execute(any(DefaultRedisScript.class), eq(List.of(REDIS_KEY)),
                anyString(), storedValue.capture(), anyString());
        var status = objectMapper.readTree(storedValue.getValue()).get("http_status");
        assertTrue(status == null || status.isNull());
        assertEquals("CONFLICT", objectMapper.readTree(storedValue.getValue()).get("error_type").asText());
    }

    @Test
    @DisplayName("Replays a stored business error as a BusinessException")
    void execute_replaysStoredBusinessError() throws Exception {
        ObjectNode record = objectMapper.createObjectNode();
        record.put("state", "COMPLETED");
        record.put("outcome", "ERROR");
        record.put("fingerprint", FINGERPRINT);
        record.put("error_code", "ORDER_NOT_SETTLABLE");
        record.put("error_type", "CONFLICT");
        record.put("message", "Order is not eligible for payment settlement");
        record.set("details", objectMapper.createObjectNode());
        when(valueOperations.get(REDIS_KEY)).thenReturn(objectMapper.writeValueAsString(record));

        ReplayedBusinessException exception = assertThrows(ReplayedBusinessException.class, () ->
                idempotencyService.execute(REDIS_KEY, FINGERPRINT, TTL, TestResult.class,
                        () -> Optional.empty(), () -> new TestResult("NEW", 0)));

        assertEquals("ORDER_NOT_SETTLABLE", exception.getErrorCode().code());
        assertEquals(ErrorType.CONFLICT, exception.getErrorCode().type());
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    private String completedSuccessRecord(TestResult testResult) throws Exception {
        ObjectNode record = objectMapper.createObjectNode();
        record.put("state", "COMPLETED");
        record.put("outcome", "SUCCESS");
        record.put("fingerprint", FINGERPRINT);
        record.set("response", objectMapper.valueToTree(testResult));
        return objectMapper.writeValueAsString(record);
    }
}
