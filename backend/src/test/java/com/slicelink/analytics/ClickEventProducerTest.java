package com.slicelink.analytics;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class ClickEventProducerTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private KafkaProperties properties;
    private ClickEventProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        properties = new KafkaProperties("slicelink.url.clicks", "slicelink-analytics");
        producer = new ClickEventProducer(kafkaTemplate, properties);
    }

    @Nested
    @DisplayName("Successful Publication")
    class SuccessTests {

        @Test
        @DisplayName("send publishes ClickEvent to configured topic with shortCode as message key")
        void send_publishesToTopicWithShortCodeKey() {
            ClickEvent event = new ClickEvent(
                    UUID.randomUUID().toString(),
                    100L,
                    "3D7gK",
                    1L,
                    Instant.now()
            );

            when(kafkaTemplate.send(eq("slicelink.url.clicks"), eq("3D7gK"), eq(event)))
                    .thenReturn(CompletableFuture.completedFuture(null));

            producer.send(event);

            verify(kafkaTemplate).send("slicelink.url.clicks", "3D7gK", event);
        }

        @Test
        @DisplayName("send does nothing when event is null or shortCode is null")
        void send_whenNullInput_doesNothing() {
            producer.send(null);
            producer.send(new ClickEvent("id", 1L, null, 1L, Instant.now()));

            verify(kafkaTemplate, never()).send(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Error Resilience")
    class ResilienceTests {

        @Test
        @DisplayName("send handles synchronous Kafka exception without throwing or breaking caller")
        void send_whenSynchronousException_doesNotThrow() {
            ClickEvent event = new ClickEvent("id1", 100L, "3D7gK", 1L, Instant.now());
            when(kafkaTemplate.send(any(), any(), any()))
                    .thenThrow(new RuntimeException("Kafka broker unavailable"));

            assertThatCode(() -> producer.send(event)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("send handles asynchronous future completion failure gracefully")
        void send_whenFutureFails_doesNotThrow() {
            ClickEvent event = new ClickEvent("id2", 100L, "3D7gK", 1L, Instant.now());
            CompletableFuture<org.springframework.kafka.support.SendResult<String, Object>> failedFuture =
                    CompletableFuture.failedFuture(new RuntimeException("Network timeout"));

            when(kafkaTemplate.send(eq("slicelink.url.clicks"), eq("3D7gK"), eq(event)))
                    .thenReturn(failedFuture);

            assertThatCode(() -> producer.send(event)).doesNotThrowAnyException();
        }
    }
}
