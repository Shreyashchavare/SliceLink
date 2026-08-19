package com.slicelink.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RateLimitServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RateLimitProperties properties;
    private RateLimitService rateLimitService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        properties = new RateLimitProperties(
                new RateLimitProperties.LoginRateLimit(5, Duration.ofMinutes(1)),
                new RateLimitProperties.UrlCreateRateLimit(20, Duration.ofMinutes(1))
        );

        rateLimitService = new RateLimitService(redisTemplate, properties);
    }

    @Nested
    @DisplayName("Key Construction & Namespacing")
    class KeyConstructionTests {

        @Test
        @DisplayName("buildLoginKey normalizes email to lowercase trimmed key")
        void buildLoginKey_normalizesInput() {
            String key = rateLimitService.buildLoginKey("  User@Example.COM  ");
            assertThat(key).isEqualTo("slicelink:rate-limit:login:user@example.com");
        }

        @Test
        @DisplayName("buildUrlCreateKey namespaces by user ID")
        void buildUrlCreateKey_formatsKeyCorrectly() {
            String key = rateLimitService.buildUrlCreateKey(42L);
            assertThat(key).isEqualTo("slicelink:rate-limit:url-create:42");
        }
    }

    @Nested
    @DisplayName("Login Rate Limiting")
    class LoginRateLimitTests {

        @Test
        @DisplayName("allowLogin sets TTL on first request (count == 1) and returns true")
        void allowLogin_firstRequest_setsTtlAndAllows() {
            when(valueOperations.increment("slicelink:rate-limit:login:test@example.com")).thenReturn(1L);

            boolean allowed = rateLimitService.allowLogin("test@example.com");

            assertThat(allowed).isTrue();
            verify(redisTemplate).expire("slicelink:rate-limit:login:test@example.com", Duration.ofMinutes(1));
        }

        @Test
        @DisplayName("allowLogin allows subsequent requests under the maximum limit without resetting TTL")
        void allowLogin_underLimit_allows() {
            when(valueOperations.increment("slicelink:rate-limit:login:test@example.com")).thenReturn(3L);

            boolean allowed = rateLimitService.allowLogin("test@example.com");

            assertThat(allowed).isTrue();
            verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("allowLogin allows request exactly at the maximum limit")
        void allowLogin_atLimit_allows() {
            when(valueOperations.increment("slicelink:rate-limit:login:test@example.com")).thenReturn(5L);

            boolean allowed = rateLimitService.allowLogin("test@example.com");

            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("allowLogin rejects request when count exceeds maximum limit")
        void allowLogin_exceedingLimit_rejects() {
            when(valueOperations.increment("slicelink:rate-limit:login:test@example.com")).thenReturn(6L);

            boolean allowed = rateLimitService.allowLogin("test@example.com");

            assertThat(allowed).isFalse();
        }

        @Test
        @DisplayName("allowLogin returns true for null or blank input without querying Redis")
        void allowLogin_nullOrBlank_returnsTrue() {
            assertThat(rateLimitService.allowLogin(null)).isTrue();
            assertThat(rateLimitService.allowLogin("   ")).isTrue();
            verify(valueOperations, never()).increment(anyString());
        }
    }

    @Nested
    @DisplayName("URL Creation Rate Limiting")
    class UrlCreateRateLimitTests {

        @Test
        @DisplayName("allowUrlCreation sets TTL on first request and returns true")
        void allowUrlCreation_firstRequest_setsTtlAndAllows() {
            when(valueOperations.increment("slicelink:rate-limit:url-create:10")).thenReturn(1L);

            boolean allowed = rateLimitService.allowUrlCreation(10L);

            assertThat(allowed).isTrue();
            verify(redisTemplate).expire("slicelink:rate-limit:url-create:10", Duration.ofMinutes(1));
        }

        @Test
        @DisplayName("allowUrlCreation allows requests under limit (e.g. 20)")
        void allowUrlCreation_underLimit_allows() {
            when(valueOperations.increment("slicelink:rate-limit:url-create:10")).thenReturn(20L);

            boolean allowed = rateLimitService.allowUrlCreation(10L);

            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("allowUrlCreation rejects request exceeding limit (21 > 20)")
        void allowUrlCreation_exceedingLimit_rejects() {
            when(valueOperations.increment("slicelink:rate-limit:url-create:10")).thenReturn(21L);

            boolean allowed = rateLimitService.allowUrlCreation(10L);

            assertThat(allowed).isFalse();
        }

        @Test
        @DisplayName("allowUrlCreation returns true for null user ID")
        void allowUrlCreation_nullUserId_returnsTrue() {
            assertThat(rateLimitService.allowUrlCreation(null)).isTrue();
            verify(valueOperations, never()).increment(anyString());
        }
    }

    @Nested
    @DisplayName("Fail-Open Policy (Redis Resilience)")
    class FailOpenTests {

        @Test
        @DisplayName("allowLogin fails open (returns true) when Redis connection fails")
        void allowLogin_redisConnectionFailure_failsOpen() {
            when(valueOperations.increment(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Redis offline"));

            boolean allowed = rateLimitService.allowLogin("user@example.com");

            assertThat(allowed).isTrue();
        }

        @Test
        @DisplayName("allowUrlCreation fails open (returns true) when Redis command times out")
        void allowUrlCreation_redisTimeout_failsOpen() {
            when(valueOperations.increment(anyString()))
                    .thenThrow(new QueryTimeoutException("Redis command timed out"));

            boolean allowed = rateLimitService.allowUrlCreation(1L);

            assertThat(allowed).isTrue();
        }
    }
}
