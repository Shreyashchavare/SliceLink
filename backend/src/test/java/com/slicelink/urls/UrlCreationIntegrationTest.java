package com.slicelink.urls;

import static org.assertj.core.api.Assertions.assertThat;

import com.slicelink.auth.AuthenticationResponse;
import com.slicelink.auth.RegisterRequest;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

/**
 * HTTP-layer integration tests for the Phase 3 URL creation endpoint.
 *
 * <p>Uses a real Spring context with H2 in-memory database, Flyway migrations,
 * and Spring Security filter chain.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UrlCreationIntegrationTest {

    private static final String URLS_ENDPOINT = "/api/v1/urls";
    private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UrlRepository urlRepository;

    private String accessToken;
    private Long currentUserId;

    @BeforeEach
    void setUp() {
        String email = "urltester" + System.nanoTime() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(email, "Password123!", "URL Tester");
        ResponseEntity<AuthenticationResponse> authResponse = rest.postForEntity(
                REGISTER_ENDPOINT,
                registerRequest,
                AuthenticationResponse.class
        );
        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(authResponse.getBody()).isNotNull();
        this.accessToken = authResponse.getBody().accessToken();
        this.currentUserId = authResponse.getBody().user().id();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    // ==================================================================
    // Successful URL Creation
    // ==================================================================

    @Test
    @DisplayName("authenticated URL creation returns 201 Created and UrlResponse")
    void createUrl_authenticated_returns201AndUrlResponse() {
        String originalUrl = "https://example.com/articles/deep-dive-into-systems";
        CreateUrlRequest request = new CreateUrlRequest(originalUrl);
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request, authHeaders());

        ResponseEntity<UrlResponse> response = rest.postForEntity(
                URLS_ENDPOINT,
                entity,
                UrlResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UrlResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isPositive();
        assertThat(body.userId()).isEqualTo(currentUserId);
        assertThat(body.originalUrl()).isEqualTo(originalUrl);
        assertThat(body.shortCode()).isNotEmpty();
        assertThat(body.status()).isEqualTo(UrlStatus.ACTIVE);
        assertThat(body.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("created URL is persisted in the database and belongs to authenticated user")
    void createUrl_persistsUrlWithCorrectOwnerInDatabase() {
        String originalUrl = "https://example.com/pricing";
        CreateUrlRequest request = new CreateUrlRequest(originalUrl);
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request, authHeaders());

        ResponseEntity<UrlResponse> response = rest.postForEntity(
                URLS_ENDPOINT,
                entity,
                UrlResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UrlResponse body = response.getBody();
        assertThat(body).isNotNull();

        Optional<Url> persisted = urlRepository.findById(body.id());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getUser().getId()).isEqualTo(currentUserId);
        assertThat(persisted.get().getOriginalUrl()).isEqualTo(originalUrl);
        assertThat(persisted.get().getShortCode()).isEqualTo(body.shortCode());
        assertThat(persisted.get().getStatus()).isEqualTo(UrlStatus.ACTIVE);
        assertThat(persisted.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("multiple URL creations produce unique short codes")
    void createMultipleUrls_generatesUniqueShortCodes() {
        Set<String> shortCodes = new HashSet<>();
        int count = 5;

        for (int i = 0; i < count; i++) {
            String originalUrl = "https://example.com/page/" + i;
            CreateUrlRequest request = new CreateUrlRequest(originalUrl);
            HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request, authHeaders());

            ResponseEntity<UrlResponse> response = rest.postForEntity(
                    URLS_ENDPOINT,
                    entity,
                    UrlResponse.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            shortCodes.add(response.getBody().shortCode());
        }

        assertThat(shortCodes).hasSize(count);
    }

    // ==================================================================
    // Authentication & Security
    // ==================================================================

    @Test
    @DisplayName("unauthenticated URL creation returns 401 UNAUTHORIZED")
    void createUrl_unauthenticated_returns401() {
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/public");
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request);

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                URLS_ENDPOINT,
                HttpMethod.POST,
                entity,
                MAP_TYPE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("UNAUTHORIZED");
        assertThat(body.get("status")).isEqualTo(401);
    }

    @Test
    @DisplayName("URL creation with invalid JWT returns 401 UNAUTHORIZED")
    void createUrl_invalidJwt_returns401() {
        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.setBearerAuth("invalid.jwt.token");
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/secret");
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request, invalidHeaders);

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                URLS_ENDPOINT,
                HttpMethod.POST,
                entity,
                MAP_TYPE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("UNAUTHORIZED");
    }

    // ==================================================================
    // Request Validation
    // ==================================================================

    @Test
    @DisplayName("invalid URL format returns 400 Bad Request with VALIDATION_FAILED")
    void createUrl_invalidFormat_returns400() {
        CreateUrlRequest request = new CreateUrlRequest("not-a-valid-url");
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request, authHeaders());

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                URLS_ENDPOINT,
                HttpMethod.POST,
                entity,
                MAP_TYPE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(body.get("status")).isEqualTo(400);
    }

    @Test
    @DisplayName("blank originalUrl returns 400 Bad Request with VALIDATION_FAILED")
    void createUrl_blankUrl_returns400() {
        CreateUrlRequest request = new CreateUrlRequest("   ");
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request, authHeaders());

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                URLS_ENDPOINT,
                HttpMethod.POST,
                entity,
                MAP_TYPE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(body.get("status")).isEqualTo(400);
    }

    @Test
    @DisplayName("URL exceeding 2048 characters returns 400 Bad Request with VALIDATION_FAILED")
    void createUrl_tooLongUrl_returns400() {
        String longUrl = "https://example.com/" + "a".repeat(2050);
        CreateUrlRequest request = new CreateUrlRequest(longUrl);
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request, authHeaders());

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                URLS_ENDPOINT,
                HttpMethod.POST,
                entity,
                MAP_TYPE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("VALIDATION_FAILED");
        assertThat(body.get("status")).isEqualTo(400);
    }
}
