package com.ticketbox.api.module.shared.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Component
public class SingleFlight {

    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();

    public <T> T execute(String key, Supplier<T> supplier) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        CompletableFuture<Object> existing = inFlight.putIfAbsent(key, future);

        if (existing != null) {
            log.debug("SingleFlight hit for key: {}. Coalescing concurrent request.", key);
            try {
                return (T) existing.join();
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(cause != null ? cause : e);
            }
        }

        try {
            T result = supplier.get();
            future.complete(result);
            return result;
        } catch (Throwable t) {
            future.completeExceptionally(t);
            throw t;
        } finally {
            inFlight.remove(key);
        }
    }
}
