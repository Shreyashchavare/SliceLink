package com.slicelink.urls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.slicelink.shared.ApiException;
import com.slicelink.users.User;
import com.slicelink.users.UserRepository;
import com.slicelink.users.UserStatus;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Tests for Redis cache-aside caching, cache HIT/MISS behavior,
 * eviction, and Redis failure resilience during URL redirection.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UrlCacheIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private UrlIdGenerator idGenerator;

    private StringRedisTemplate mockRedisTemplate;
    private ValueOperations<String, String> mockValueOperations;
    private Map<String, String> inMemoryRedisStore;
    private UrlCacheProperties properties;
    private UrlCacheService urlCacheService;
    private UrlRepository mockUrlRepository;
    private UrlService urlService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        inMemoryRedisStore = new ConcurrentHashMap<>();
        mockRedisTemplate = mock(StringRedisTemplate.class);
        mockValueOperations = mock(ValueOperations.class);
        when(mockRedisTemplate.opsForValue()).thenReturn(mockValueOperations);

        // Wire in-memory store simulation to mockValueOperations for realistic Redis cache testing
        when(mockValueOperations.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return inMemoryRedisStore.get(key);
        });

        org.mockito.Mockito.doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            inMemoryRedisStore.put(key, value);
            return null;
        }).when(mockValueOperations).set(anyString(), anyString(), any(Duration.class));

        when(mockRedisTemplate.delete(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return inMemoryRedisStore.remove(key) != null;
        });

        properties = new UrlCacheProperties(Duration.ofHours(24), "url:redirect:");
        urlCacheService = new UrlCacheService(mockRedisTemplate, properties);

        mockUrlRepository = mock(UrlRepository.class);
        when(mockUrlRepository.findByShortCode(anyString())).thenAnswer(inv -> urlRepository.findByShortCode(inv.getArgument(0)));
        when(mockUrlRepository.save(any(Url.class))).thenAnswer(inv -> urlRepository.save(inv.getArgument(0)));
        when(mockUrlRepository.existsByShortCode(anyString())).thenAnswer(inv -> urlRepository.existsByShortCode(inv.getArgument(0)));

        com.slicelink.analytics.ClickEventProducer mockProducer = mock(com.slicelink.analytics.ClickEventProducer.class);
        urlService = new UrlService(mockUrlRepository, userRepository, idGenerator, urlCacheService, mockProducer);
    }

    private User createTestUser() {
        String email = "cacheuser" + System.nanoTime() + "@example.com";
        return userRepository.save(new User(email, "hashedPassword", "Cache Tester", UserStatus.ACTIVE));
    }

    private Url createUrl(User user, String originalUrl, UrlStatus status) {
        long id = idGenerator.nextId();
        String shortCode = Base62.encode(id);
        return urlRepository.save(new Url(id, user, originalUrl, shortCode, status));
    }

    // ==================================================================
    // Cache-Aside Flow: HIT / MISS / Eviction
    // ==================================================================

    @Nested
    @DisplayName("Cache-Aside Flow")
    class CacheAsideTests {

        @Test
        @DisplayName("first request is a cache MISS, queries database, and populates Redis")
        void getRedirectUrl_cacheMiss_queriesDatabaseAndPopulatesRedis() {
            User user = createTestUser();
            String originalUrl = "https://example.com/cache-miss-test";
            Url url = createUrl(user, originalUrl, UrlStatus.ACTIVE);
            String shortCode = url.getShortCode();
            String expectedCacheKey = "url:redirect:" + shortCode;

            // Initially cache is empty
            assertThat(inMemoryRedisStore).doesNotContainKey(expectedCacheKey);

            // First call -> Cache MISS
            String redirectUrl = urlService.getRedirectUrl(shortCode);

            assertThat(redirectUrl).isEqualTo(originalUrl);
            verify(mockUrlRepository, times(1)).findByShortCode(shortCode);
            assertThat(inMemoryRedisStore).containsEntry(expectedCacheKey, originalUrl);
        }

        @Test
        @DisplayName("second request is a cache HIT, returns destination, and does NOT query database")
        void getRedirectUrl_cacheHit_doesNotQueryDatabase() {
            User user = createTestUser();
            String originalUrl = "https://example.com/cache-hit-test";
            Url url = createUrl(user, originalUrl, UrlStatus.ACTIVE);
            String shortCode = url.getShortCode();

            // First call -> loads into cache
            urlService.getRedirectUrl(shortCode);
            verify(mockUrlRepository, times(1)).findByShortCode(shortCode);

            // Reset invocation counts on the database repository spy
            clearInvocations(mockUrlRepository);

            // Second call -> should be a Cache HIT from Redis
            String cachedRedirectUrl = urlService.getRedirectUrl(shortCode);

            assertThat(cachedRedirectUrl).isEqualTo(originalUrl);
            verify(mockUrlRepository, never()).findByShortCode(anyString());
        }

        @Test
        @DisplayName("evict removes entry from Redis, causing next request to query database again")
        void getRedirectUrl_afterEvict_queriesDatabaseAgain() {
            User user = createTestUser();
            String originalUrl = "https://example.com/evict-test";
            Url url = createUrl(user, originalUrl, UrlStatus.ACTIVE);
            String shortCode = url.getShortCode();
            String expectedCacheKey = "url:redirect:" + shortCode;

            // 1. Populate cache
            urlService.getRedirectUrl(shortCode);
            assertThat(inMemoryRedisStore).containsKey(expectedCacheKey);

            // 2. Invalidate cache
            urlCacheService.evict(shortCode);
            assertThat(inMemoryRedisStore).doesNotContainKey(expectedCacheKey);

            // 3. Next request should hit database again
            clearInvocations(mockUrlRepository);
            String resultAfterEvict = urlService.getRedirectUrl(shortCode);

            assertThat(resultAfterEvict).isEqualTo(originalUrl);
            verify(mockUrlRepository, times(1)).findByShortCode(shortCode);
        }

        @Test
        @DisplayName("multiple short codes have independent cache entries")
        void getRedirectUrl_multipleShortCodes_independentCacheKeys() {
            User user = createTestUser();
            Url urlA = createUrl(user, "https://example.com/alpha", UrlStatus.ACTIVE);
            Url urlB = createUrl(user, "https://example.com/beta", UrlStatus.ACTIVE);

            urlService.getRedirectUrl(urlA.getShortCode());
            urlService.getRedirectUrl(urlB.getShortCode());

            assertThat(inMemoryRedisStore)
                    .containsEntry("url:redirect:" + urlA.getShortCode(), "https://example.com/alpha")
                    .containsEntry("url:redirect:" + urlB.getShortCode(), "https://example.com/beta");
        }
    }

    // ==================================================================
    // Status & Negative Caching Rules
    // ==================================================================

    @Nested
    @DisplayName("Status & Negative Caching Rules")
    class NegativeCachingTests {

        @Test
        @DisplayName("unknown short code throws 404 URL_NOT_FOUND and is NOT cached")
        void getRedirectUrl_unknownShortCode_notCached() {
            assertThatThrownBy(() -> urlService.getRedirectUrl("unknownCode123"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(apiEx.getCode()).isEqualTo("URL_NOT_FOUND");
                    });

            assertThat(inMemoryRedisStore).doesNotContainKey("url:redirect:unknownCode123");
        }

        @Test
        @DisplayName("disabled short code throws 410 URL_DISABLED and is NOT cached as active redirect")
        void getRedirectUrl_disabledShortCode_notCached() {
            User user = createTestUser();
            Url disabledUrl = createUrl(user, "https://example.com/disabled", UrlStatus.DISABLED);
            String shortCode = disabledUrl.getShortCode();

            assertThatThrownBy(() -> urlService.getRedirectUrl(shortCode))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.GONE);
                        assertThat(apiEx.getCode()).isEqualTo("URL_DISABLED");
                    });

            assertThat(inMemoryRedisStore).doesNotContainKey("url:redirect:" + shortCode);
        }
    }

    // ==================================================================
    // Redis Failure Resilience (Graceful Fallback)
    // ==================================================================

    @Nested
    @DisplayName("Redis Failure Resilience")
    class ResilienceTests {

        @Test
        @DisplayName("Redis GET failure falls back to PostgreSQL and returns destination URL")
        void getRedirectUrl_whenRedisGetFails_fallsBackToPostgreSQL() {
            User user = createTestUser();
            String originalUrl = "https://example.com/redis-get-failure";
            Url url = createUrl(user, originalUrl, UrlStatus.ACTIVE);
            String shortCode = url.getShortCode();

            // Simulate Redis GET throwing connection exception
            when(mockValueOperations.get(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Redis server is down"));

            String result = urlService.getRedirectUrl(shortCode);

            assertThat(result).isEqualTo(originalUrl);
            verify(mockUrlRepository, times(1)).findByShortCode(shortCode);
        }

        @Test
        @DisplayName("Redis PUT failure does not abort redirect and returns destination URL")
        void getRedirectUrl_whenRedisPutFails_returnsDestination() {
            User user = createTestUser();
            String originalUrl = "https://example.com/redis-put-failure";
            Url url = createUrl(user, originalUrl, UrlStatus.ACTIVE);
            String shortCode = url.getShortCode();

            // Redis GET returns empty, PUT throws exception
            when(mockValueOperations.get(anyString())).thenReturn(null);
            org.mockito.Mockito.doThrow(new RedisConnectionFailureException("Redis write timeout"))
                    .when(mockValueOperations).set(anyString(), anyString(), any(Duration.class));

            String result = urlService.getRedirectUrl(shortCode);

            assertThat(result).isEqualTo(originalUrl);
            verify(mockUrlRepository, times(1)).findByShortCode(shortCode);
        }

        @Test
        @DisplayName("Redis completely unavailable still permits successful redirect via PostgreSQL without 5xx error")
        void getRedirectUrl_whenRedisCompletelyDown_doesNotThrow5xx() {
            User user = createTestUser();
            String originalUrl = "https://example.com/redis-down";
            Url url = createUrl(user, originalUrl, UrlStatus.ACTIVE);
            String shortCode = url.getShortCode();

            // All Redis operations fail
            when(mockValueOperations.get(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));
            org.mockito.Mockito.doThrow(new RedisConnectionFailureException("Connection refused"))
                    .when(mockValueOperations).set(anyString(), anyString(), any(Duration.class));
            when(mockRedisTemplate.delete(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));

            // Redirect succeeds without throwing 5xx exception
            String result = urlService.getRedirectUrl(shortCode);

            assertThat(result).isEqualTo(originalUrl);
            verify(mockUrlRepository, times(1)).findByShortCode(shortCode);
        }
    }
}
