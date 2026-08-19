package com.slicelink.analytics;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClickEventConsumerTest {

    private AnalyticsService analyticsService;
    private ClickEventConsumer consumer;

    @BeforeEach
    void setUp() {
        analyticsService = mock(AnalyticsService.class);
        consumer = new ClickEventConsumer(analyticsService);
    }

    @Test
    @DisplayName("consume records valid ClickEvent via AnalyticsService")
    void consume_validEvent_callsAnalyticsService() {
        ClickEvent event = new ClickEvent(
                UUID.randomUUID().toString(),
                100L,
                "3D7gK",
                1L,
                Instant.now()
        );

        consumer.consume(event);

        verify(analyticsService).recordClick(event);
    }

    @Test
    @DisplayName("consume does nothing when event or eventId is null")
    void consume_nullEvent_doesNothing() {
        consumer.consume(null);
        consumer.consume(new ClickEvent(null, 100L, "3D7gK", 1L, Instant.now()));

        verify(analyticsService, never()).recordClick(any());
    }

    @Test
    @DisplayName("consume catches unexpected service exception gracefully without propagating to Kafka listener container")
    void consume_whenServiceThrows_doesNotPropagate() {
        ClickEvent event = new ClickEvent("id123", 100L, "3D7gK", 1L, Instant.now());
        doThrow(new RuntimeException("Database error")).when(analyticsService).recordClick(event);

        assertThatCode(() -> consumer.consume(event)).doesNotThrowAnyException();
    }
}
