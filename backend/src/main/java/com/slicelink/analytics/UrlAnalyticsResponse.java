package com.slicelink.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Response body for URL click analytics.
 */
@Schema(description = "Analytics summary for a shortened URL")
public record UrlAnalyticsResponse(
        @Schema(description = "Internal numeric URL identifier")
        Long urlId,

        @Schema(description = "Base62 short code",
                example = "3D7gK")
        String shortCode,

        @Schema(description = "Total number of recorded clicks")
        long totalClicks,

        @Schema(description = "Recent click timestamps (up to 10 newest, in UTC)")
        List<Instant> recentClicks
) {
}
