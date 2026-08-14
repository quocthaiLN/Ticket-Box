package com.ticketbox.api.module.shared.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SingleFlightTest {

    private SingleFlight singleFlight;

    @BeforeEach
    void setUp() {
        singleFlight = new SingleFlight();
    }

    @Test
    @DisplayName("Should execute supplier only once when multiple threads request same key concurrently")
    void execute_ConcurrentRequests_ExecutesSupplierOnce() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger dbCallCounter = new AtomicInteger(0);

        ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String result = singleFlight.execute("concerts:123", () -> {
                        dbCallCounter.incrementAndGet();
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "Concert Data";
                    });
                    results.add(result);
                } catch (Exception e) {
                    fail("Exception occurred: " + e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(5, TimeUnit.SECONDS);

        executor.shutdown();

        assertTrue(completed, "All threads should complete");
        assertEquals(1, dbCallCounter.get(), "Database fallback supplier should be called exactly ONCE");
        assertEquals(threadCount, results.size(), "All threads should receive result");
        assertTrue(results.stream().allMatch("Concert Data"::equals), "All threads should receive identical result");
    }

    @Test
    @DisplayName("Should execute fresh call after previous key execution completes")
    void execute_SequentialRequests_ExecutesSupplierEachTime() {
        AtomicInteger counter = new AtomicInteger(0);

        String first = singleFlight.execute("key1", () -> "Result " + counter.incrementAndGet());
        String second = singleFlight.execute("key1", () -> "Result " + counter.incrementAndGet());

        assertEquals("Result 1", first);
        assertEquals("Result 2", second);
        assertEquals(2, counter.get());
    }
}
