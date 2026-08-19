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
        Instant createdAt,

        @Schema(description = "Last update timestamp (UTC)")
        Instant updatedAt) {

    public UrlResponse(Long id, Long userId, String originalUrl, String shortCode, UrlStatus status, Instant createdAt) {
        this(id, userId, originalUrl, shortCode, status, createdAt, null);
    }

    /** Converts a {@link Url} entity to its response representation. */
    public static UrlResponse from(Url url) {
        return new UrlResponse(
                url.getId(),
                url.getUser().getId(),
                url.getOriginalUrl(),
                url.getShortCode(),
                url.getStatus(),
                url.getCreatedAt(),
                url.getUpdatedAt());
    }
}
