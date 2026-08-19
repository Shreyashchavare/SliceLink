package com.slicelink.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for Kafka event streaming.
 */
@ConfigurationProperties(prefix = "slicelink.kafka")
public record KafkaProperties(
        @DefaultValue("slicelink.url.clicks") String clickTopic,
        @DefaultValue("slicelink-analytics") String consumerGroup
) {
    public KafkaProperties {
        if (clickTopic == null || clickTopic.isBlank()) {
            clickTopic = "slicelink.url.clicks";
        }
        if (consumerGroup == null || consumerGroup.isBlank()) {
            consumerGroup = "slicelink-analytics";
        }
    }
}
