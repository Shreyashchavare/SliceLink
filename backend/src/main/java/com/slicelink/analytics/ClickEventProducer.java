package com.slicelink.analytics;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Kafka producer for asynchronous publication of URL click events.
 *
 * <p>Never blocks the caller and catches all Kafka transport exceptions
 * so that analytics outages cannot impact URL redirection availability.
 */
@Component
@EnableConfigurationProperties(KafkaProperties.class)
public class ClickEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ClickEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaProperties properties;

    public ClickEventProducer(KafkaTemplate<String, Object> kafkaTemplate, KafkaProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    /**
     * Publishes a click event to Kafka asynchronously using the shortCode as the message key.
     *
     * @param event the click event to publish
     */
    public void send(ClickEvent event) {
        if (event == null || event.shortCode() == null) {
            return;
        }

        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(properties.clickTopic(), event.shortCode(), event);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Failed to publish click event to Kafka for short code {}: {}",
                            event.shortCode(), ex.getMessage());
                } else {
                    log.debug("Published click event {} to topic {}", event.eventId(), properties.clickTopic());
                }
            });
        } catch (Exception e) {
            log.warn("Kafka send threw exception for short code {}: {}", event.shortCode(), e.getMessage());
        }
    }
}
