package com.slicelink.urls;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Response body for a created or retrieved shortened URL.
 */
@Schema(description = "Shortened URL information")
public record UrlResponse(

        @Schema(description = "Internal numeric record identifier")
        Long id,

        @Schema(description = "ID of the user who created this URL")
        Long userId,

        @Schema(description = "The original long URL",
                example = "https://www.example.com/some/long/path?with=params")
        String originalUrl,

        @Schema(description = "Base62-encoded short code",
                example = "3D7gK")
        String shortCode,

        @Schema(description = "Lifecycle status of the URL")
        UrlStatus status,

        @Schema(description = "Creation timestamp (UTC)")
        Instant createdAt) {

    /** Converts a {@link Url} entity to its response representation. */
    public static UrlResponse from(Url url) {
        return new UrlResponse(
                url.getId(),
                url.getUser().getId(),
                url.getOriginalUrl(),
                url.getShortCode(),
                url.getStatus(),
                url.getCreatedAt());
    }
}
