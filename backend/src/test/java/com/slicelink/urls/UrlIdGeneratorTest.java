package com.slicelink.urls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UrlIdGeneratorTest {

    @Test
    @DisplayName("generates positive IDs")
    void nextId_generatesPositiveId() {
        UrlIdGenerator generator = new UrlIdGenerator();
        long id = generator.nextId();
        assertThat(id).isPositive();
    }

    @Test
    @DisplayName("consecutive IDs are strictly increasing")
    void nextId_generatesMonotonicallyIncreasingIds() {
        UrlIdGenerator generator = new UrlIdGenerator();
        long prev = generator.nextId();
        for (int i = 0; i < 1000; i++) {
            long current = generator.nextId();
            assertThat(current).isGreaterThan(prev);
            prev = current;
        }
    }

    @Test
    @DisplayName("multi-threaded ID generation produces unique IDs without collision")
    void nextId_concurrentGeneration_producesUniqueIds() throws InterruptedException {
        UrlIdGenerator generator = new UrlIdGenerator();
        int threadCount = 10;
        int idsPerThread = 500;
        int totalExpectedIds = threadCount * idsPerThread;

        Set<Long> generatedIds = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < idsPerThread; j++) {
                        generatedIds.add(generator.nextId());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(generatedIds).hasSize(totalExpectedIds);
    }

    @Test
    @DisplayName("constructor validates nodeId bounds [0, 4095]")
    void constructor_validatesNodeIdBounds() {
        assertThatThrownBy(() -> new UrlIdGenerator(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nodeId must be in [0, 4095]");

        assertThatThrownBy(() -> new UrlIdGenerator(4096))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nodeId must be in [0, 4095]");

        // Valid boundary node IDs
        UrlIdGenerator gen0 = new UrlIdGenerator(0);
        UrlIdGenerator genMax = new UrlIdGenerator(4095);
        assertThat(gen0.nextId()).isPositive();
        assertThat(genMax.nextId()).isPositive();
    }

    @Test
    @DisplayName("throws IllegalStateException when system clock moves backwards")
    void nextId_clockMovingBackwards_throwsException() {
        AtomicLong mockTime = new AtomicLong(1_750_000_000_000L);

        UrlIdGenerator generator = new UrlIdGenerator() {
            @Override
            long currentMillis() {
                return mockTime.get();
            }
        };

        // First ID generation sets lastTimestamp
        long id1 = generator.nextId();
        assertThat(id1).isPositive();

        // Clock moves backwards by 50ms
        mockTime.addAndGet(-50);

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Clock moved backwards");
    }
}
