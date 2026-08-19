package com.slicelink.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens for click events and persists them idempotently.
 */
@Component
public class ClickEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClickEventConsumer.class);

    private final AnalyticsService analyticsService;

    public ClickEventConsumer(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Consumes a click event from the configured Kafka topic and records it.
     *
     * @param event the deserialized click event
     */
    @KafkaListener(
            topics = "${slicelink.kafka.click-topic:slicelink.url.clicks}",
            groupId = "${slicelink.kafka.consumer-group:slicelink-analytics}"
    )
    public void consume(ClickEvent event) {
        if (event == null || event.eventId() == null) {
            log.warn("Received null or empty click event from Kafka");
            return;
        }

        log.debug("Received click event from Kafka: eventId={}, shortCode={}", event.eventId(), event.shortCode());
        try {
            analyticsService.recordClick(event);
        } catch (Exception e) {
            log.error("Failed to process click event {}: {}", event.eventId(), e.getMessage(), e);
        }
    }
}
