package com.slicelink.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Click event data model emitted to Kafka on URL redirection.
 */
@Schema(description = "Click event payload transmitted over Kafka")
public record ClickEvent(
        @Schema(description = "Unique event identifier (UUID)")
        String eventId,

        @Schema(description = "ID of the clicked shortened URL")
        Long urlId,

        @Schema(description = "Base62 short code of the URL")
        String shortCode,

        @Schema(description = "ID of the URL owner")
        Long userId,

        @Schema(description = "Timestamp when the click occurred (UTC)")
        Instant occurredAt
) {
}
