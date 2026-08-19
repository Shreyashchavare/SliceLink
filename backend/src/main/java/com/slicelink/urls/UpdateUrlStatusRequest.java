package com.slicelink.urls;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for toggling a URL lifecycle status.
 */
@Schema(description = "URL status update request")
public record UpdateUrlStatusRequest(

        @NotNull(message = "status must not be null")
        @Schema(description = "New lifecycle status of the URL",
                example = "DISABLED")
        UrlStatus status) {
}
