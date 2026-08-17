package com.slicelink.urls;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Request body for {@code POST /api/v1/urls}.
 *
 * <p>The original URL is validated with Hibernate Validator's {@code @URL}
 * constraint which checks that the value is an absolute URL with a supported
 * protocol (http/https/ftp by default). Blank values are rejected separately
 * by {@code @NotBlank} so the validation error message is unambiguous.
 */
@Schema(description = "URL creation request")
public record CreateUrlRequest(

        @NotBlank(message = "originalUrl must not be blank")
        @Size(max = 2048, message = "originalUrl must not exceed 2048 characters")
        @URL(message = "originalUrl must be a valid absolute URL")
        @Schema(description = "The original URL to shorten",
                example = "https://www.example.com/some/long/path?with=params")
        String originalUrl) {
}
