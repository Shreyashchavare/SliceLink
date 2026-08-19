package com.slicelink.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for Redis-backed rate limiting.
 */
@ConfigurationProperties(prefix = "slicelink.rate-limit")
public record RateLimitProperties(
        LoginRateLimit login,
        UrlCreateRateLimit urlCreate
) {
    public RateLimitProperties {
        if (login == null) {
            login = new LoginRateLimit(5, Duration.ofMinutes(1));
        }
        if (urlCreate == null) {
            urlCreate = new UrlCreateRateLimit(20, Duration.ofMinutes(1));
        }
    }

    public record LoginRateLimit(
            @DefaultValue("5") int maxRequests,
            @DefaultValue("PT1M") Duration window
    ) {}

    public record UrlCreateRateLimit(
            @DefaultValue("20") int maxRequests,
            @DefaultValue("PT1M") Duration window
    ) {}
}
