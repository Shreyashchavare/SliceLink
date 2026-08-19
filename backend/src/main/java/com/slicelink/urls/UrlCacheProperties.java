package com.slicelink.urls;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for URL redirection caching.
 */
@ConfigurationProperties(prefix = "slicelink.cache")
public record UrlCacheProperties(
        @DefaultValue("PT24H") Duration urlTtl,
        @DefaultValue("url:redirect:") String keyPrefix
) {
    public UrlCacheProperties {
        if (urlTtl == null) {
            urlTtl = Duration.ofHours(24);
        }
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "url:redirect:";
        }
    }
}
