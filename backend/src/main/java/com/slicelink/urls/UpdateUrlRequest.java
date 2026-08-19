package com.slicelink.urls;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Request body for updating a URL destination.
 */
@Schema(description = "URL update request")
public record UpdateUrlRequest(

        @NotBlank(message = "originalUrl must not be blank")
        @Size(max = 2048, message = "originalUrl must not exceed 2048 characters")
        @URL(message = "originalUrl must be a valid absolute URL")
        @Schema(description = "The updated original long URL",
                example = "https://www.example.com/new/destination")
        String originalUrl) {
}
