package com.slicelink.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.slicelink.auth.AuthenticationResponse;
import com.slicelink.auth.LoginRequest;
import com.slicelink.auth.RegisterRequest;
import com.slicelink.urls.CreateUrlRequest;
import com.slicelink.urls.UrlResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration tests for Phase 8 Redis Rate Limiting & Abuse Prevention:
 * verifies protection on POST /api/v1/auth/login and POST /api/v1/urls,
 * HTTP 429 response structure, user/identifier quota isolation,
 * and fail-open resilience on Redis outages.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RateLimitingIntegrationTest {

    private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
    private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";
    private static final String URLS_ENDPOINT = "/api/v1/urls";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate rest;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    private ValueOperations<String, String> valueOperations;
    private ConcurrentHashMap<String, Long> redisCounterStore;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory(httpClient));

        redisCounterStore = new ConcurrentHashMap<>();
        valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return redisCounterStore.compute(key, (k, v) -> (v == null) ? 1L : v + 1L);
        });
    }

    private String registerAndGetToken(String emailPrefix) {
        String email = emailPrefix + System.nanoTime() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(email, "Password123!", "Rate Limit Tester");
        ResponseEntity<AuthenticationResponse> response = rest.postForEntity(
                REGISTER_ENDPOINT,
                registerRequest,
                AuthenticationResponse.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().accessToken();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    // ==================================================================
    // 1. Login Rate Limiting (POST /api/v1/auth/login)
    // ==================================================================

    @Nested
    @DisplayName("Login Rate Limiting (POST /api/v1/auth/login)")
    class LoginRateLimitIntegrationTests {

        @Test
        @DisplayName("login returns 429 RATE_LIMIT_EXCEEDED when exceeding 5 requests in window")
        void login_rateLimited_after5Attempts() {
            String email = "victim" + System.nanoTime() + "@example.com";

            // Attempts 1..5: return 401 Unauthorized for invalid password
            for (int i = 1; i <= 5; i++) {
                LoginRequest request = new LoginRequest(email, "WrongPassword!");
                ResponseEntity<Map<String, Object>> response = rest.exchange(
                        LOGIN_ENDPOINT,
                        HttpMethod.POST,
                        new HttpEntity<>(request),
                        MAP_TYPE
                );
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            }

            // Attempt 6: returns 429 Too Many Requests
            LoginRequest request6 = new LoginRequest(email, "WrongPassword!");
            ResponseEntity<Map<String, Object>> response6 = rest.exchange(
                    LOGIN_ENDPOINT,
                    HttpMethod.POST,
                    new HttpEntity<>(request6),
                    MAP_TYPE
            );

            assertThat(response6.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            Map<String, Object> body = response6.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("code")).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(body.get("message")).isEqualTo("Too many requests. Please try again later.");
        }

        @Test
        @DisplayName("different login identifiers have independent rate limit counters")
        void login_differentIdentifiers_haveIndependentLimits() {
            String emailA = "userA" + System.nanoTime() + "@example.com";
            String emailB = "userB" + System.nanoTime() + "@example.com";

            // Exhaust limit for user A (5 attempts)
            for (int i = 1; i <= 5; i++) {
                rest.exchange(LOGIN_ENDPOINT, HttpMethod.POST, new HttpEntity<>(new LoginRequest(emailA, "BadPass")), MAP_TYPE);
            }

            // User A is now rate-limited (6th attempt -> 429)
            ResponseEntity<Map<String, Object>> resA = rest.exchange(
                    LOGIN_ENDPOINT,
                    HttpMethod.POST,
                    new HttpEntity<>(new LoginRequest(emailA, "BadPass")),
                    MAP_TYPE
            );
            assertThat(resA.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

            // User B can still attempt login (returns 401 for bad pass, not 429)
            ResponseEntity<Map<String, Object>> resB = rest.exchange(
                    LOGIN_ENDPOINT,
                    HttpMethod.POST,
                    new HttpEntity<>(new LoginRequest(emailB, "BadPass")),
                    MAP_TYPE
            );
            assertThat(resB.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==================================================================
    // 2. URL Creation Rate Limiting (POST /api/v1/urls)
    // ==================================================================

    @Nested
    @DisplayName("URL Creation Rate Limiting (POST /api/v1/urls)")
    class UrlCreationRateLimitIntegrationTests {

        @Test
        @DisplayName("URL creation returns 429 RATE_LIMIT_EXCEEDED after exceeding 20 requests")
        void urlCreation_rateLimited_after20Creations() {
            String token = registerAndGetToken("url_ratelimit_user");
            HttpHeaders headers = authHeaders(token);

            // 1..20: successfully created (201 Created)
            for (int i = 1; i <= 20; i++) {
                CreateUrlRequest req = new CreateUrlRequest("https://example.com/target-" + i);
                ResponseEntity<UrlResponse> res = rest.postForEntity(URLS_ENDPOINT, new HttpEntity<>(req, headers), UrlResponse.class);
                assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            }

            // 21st attempt: returns 429 Too Many Requests
            CreateUrlRequest req21 = new CreateUrlRequest("https://example.com/target-21");
            ResponseEntity<Map<String, Object>> res21 = rest.exchange(
                    URLS_ENDPOINT,
                    HttpMethod.POST,
                    new HttpEntity<>(req21, headers),
                    MAP_TYPE
            );

            assertThat(res21.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            Map<String, Object> body = res21.getBody();
            assertThat(body).isNotNull();
            assertThat(body.get("code")).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(body.get("message")).isEqualTo("Too many requests. Please try again later.");
        }

        @Test
        @DisplayName("different users have independent URL creation rate limits")
        void urlCreation_differentUsers_independentLimits() {
            String tokenUserA = registerAndGetToken("user_a_limit");
            String tokenUserB = registerAndGetToken("user_b_limit");

            // Exhaust User A's limit (20 creations)
            for (int i = 1; i <= 20; i++) {
                CreateUrlRequest req = new CreateUrlRequest("https://example.com/a-" + i);
                rest.postForEntity(URLS_ENDPOINT, new HttpEntity<>(req, authHeaders(tokenUserA)), UrlResponse.class);
            }

            // User A is rate limited
            CreateUrlRequest reqA = new CreateUrlRequest("https://example.com/a-blocked");
            ResponseEntity<Map<String, Object>> resA = rest.exchange(
                    URLS_ENDPOINT,
                    HttpMethod.POST,
                    new HttpEntity<>(reqA, authHeaders(tokenUserA)),
                    MAP_TYPE
            );
            assertThat(resA.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

            // User B is NOT rate limited (201 Created)
            CreateUrlRequest reqB = new CreateUrlRequest("https://example.com/b-ok");
            ResponseEntity<UrlResponse> resB = rest.postForEntity(URLS_ENDPOINT, new HttpEntity<>(reqB, authHeaders(tokenUserB)), UrlResponse.class);
            assertThat(resB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
    }

    // ==================================================================
    // 3. Fail-Open Policy on Redis Outage
    // ==================================================================

    @Nested
    @DisplayName("Fail-Open Policy")
    class FailOpenIntegrationTests {

        @Test
        @DisplayName("login and URL creation succeed normally when Redis is offline (fail-open)")
        void operations_continueNormally_whenRedisUnavailable() {
            // Configure Redis to throw connection failure
            when(valueOperations.increment(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Simulated Redis Connection Refused"));

            String token = registerAndGetToken("failopen_user");

            // URL creation succeeds (201 Created, not 500)
            CreateUrlRequest createReq = new CreateUrlRequest("https://example.com/failopen-test");
            ResponseEntity<UrlResponse> createRes = rest.postForEntity(
                    URLS_ENDPOINT,
                    new HttpEntity<>(createReq, authHeaders(token)),
                    UrlResponse.class
            );
            assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            // Login attempt succeeds/behaves normally (401 for wrong pass, not 500)
            LoginRequest loginReq = new LoginRequest("failopen_user@example.com", "WrongPass");
            ResponseEntity<Map<String, Object>> loginRes = rest.exchange(
                    LOGIN_ENDPOINT,
                    HttpMethod.POST,
                    new HttpEntity<>(loginReq),
                    MAP_TYPE
            );
            assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
