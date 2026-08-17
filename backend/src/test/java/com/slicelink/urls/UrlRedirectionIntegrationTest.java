package com.slicelink.urls;

import static org.assertj.core.api.Assertions.assertThat;

import com.slicelink.auth.AuthenticationResponse;
import com.slicelink.auth.RegisterRequest;
import com.slicelink.users.User;
import com.slicelink.users.UserRepository;
import com.slicelink.users.UserStatus;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;

/**
 * HTTP-layer integration tests for the Phase 4 URL redirection endpoint.
 *
 * <p>Uses a real Spring context with H2 in-memory database, Flyway migrations,
 * and Spring Security filter chain.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UrlRedirectionIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";
    private static final String URLS_ENDPOINT = "/api/v1/urls";
    private static final String ME_ENDPOINT = "/api/v1/users/me";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private UrlIdGenerator idGenerator;

    @BeforeEach
    void setUp() {
        rest.getRestTemplate().setRequestFactory(new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });
    }

    private User createTestUser() {
        String email = "redirectuser" + System.nanoTime() + "@example.com";
        return userRepository.save(new User(email, "hashedPassword", "Redirect Tester", UserStatus.ACTIVE));
    }

    private Url createUrl(User user, String originalUrl, UrlStatus status) {
        long id = idGenerator.nextId();
        String shortCode = Base62.encode(id);
        return urlRepository.save(new Url(id, user, originalUrl, shortCode, status));
    }

    // ==================================================================
    // Successful Public Redirection (302 Found)
    // ==================================================================

    @Test
    @DisplayName("active short code returns 302 Found and Location header without authentication")
    void redirect_whenActiveShortCode_returns302AndLocationHeader() {
        User user = createTestUser();
        String originalUrl = "https://example.com/docs/getting-started";
        Url url = createUrl(user, originalUrl, UrlStatus.ACTIVE);

        ResponseEntity<Void> response = rest.getForEntity("/" + url.getShortCode(), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create(originalUrl));
    }

    @Test
    @DisplayName("multiple short codes resolve to their respective target URLs")
    void redirect_multipleShortCodes_resolveToCorrectDestinations() {
        User user = createTestUser();
        String url1Target = "https://example.com/target-one";
        String url2Target = "https://example.com/target-two";

        Url url1 = createUrl(user, url1Target, UrlStatus.ACTIVE);
        Url url2 = createUrl(user, url2Target, UrlStatus.ACTIVE);

        ResponseEntity<Void> response1 = rest.getForEntity("/" + url1.getShortCode(), Void.class);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response1.getHeaders().getLocation()).isEqualTo(URI.create(url1Target));

        ResponseEntity<Void> response2 = rest.getForEntity("/" + url2.getShortCode(), Void.class);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response2.getHeaders().getLocation()).isEqualTo(URI.create(url2Target));
    }

    @Test
    @DisplayName("end-to-end: create URL with JWT and redirect publicly without JWT")
    void createAndRedirect_endToEndFlow() {
        // 1. Register a user
        String email = "e2euser" + System.nanoTime() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(email, "Password123!", "E2E Tester");
        ResponseEntity<AuthenticationResponse> authResponse = rest.postForEntity(
                REGISTER_ENDPOINT,
                registerRequest,
                AuthenticationResponse.class
        );
        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(authResponse.getBody()).isNotNull();
        String token = authResponse.getBody().accessToken();

        // 2. Create a shortened URL with JWT
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);
        String targetUrl = "https://example.com/blog/2026/08/release-notes";
        CreateUrlRequest createRequest = new CreateUrlRequest(targetUrl);
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(createRequest, authHeaders);

        ResponseEntity<UrlResponse> createResponse = rest.postForEntity(
                URLS_ENDPOINT,
                entity,
                UrlResponse.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        String shortCode = createResponse.getBody().shortCode();

        // 3. Public redirect without JWT
        ResponseEntity<Void> redirectResponse = rest.getForEntity("/" + shortCode, Void.class);
        assertThat(redirectResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirectResponse.getHeaders().getLocation()).isEqualTo(URI.create(targetUrl));
    }

    // ==================================================================
    // Error Handling (404 Not Found, 410 Gone)
    // ==================================================================

    @Test
    @DisplayName("unknown short code returns 404 Not Found with URL_NOT_FOUND code")
    void redirect_whenUnknownShortCode_returns404() {
        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/nonexistent999",
                HttpMethod.GET,
                null,
                MAP_TYPE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("URL_NOT_FOUND");
        assertThat(body.get("status")).isEqualTo(404);
    }

    @Test
    @DisplayName("disabled short code returns 410 Gone with URL_DISABLED code")
    void redirect_whenDisabledUrl_returns410() {
        User user = createTestUser();
        String originalUrl = "https://example.com/deprecated";
        Url url = createUrl(user, originalUrl, UrlStatus.DISABLED);

        ResponseEntity<Map<String, Object>> response = rest.exchange(
                "/" + url.getShortCode(),
                HttpMethod.GET,
                null,
                MAP_TYPE
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("code")).isEqualTo("URL_DISABLED");
        assertThat(body.get("status")).isEqualTo(410);
    }

    // ==================================================================
    // Verify Protected Endpoints Remain Protected
    // ==================================================================

    @Test
    @DisplayName("authenticated API endpoints remain protected when unauthenticated")
    void protectedEndpoints_remainProtected() {
        // POST /api/v1/urls unauthenticated -> 401
        CreateUrlRequest request = new CreateUrlRequest("https://example.com/secret");
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request);
        ResponseEntity<Map<String, Object>> urlResponse = rest.exchange(
                URLS_ENDPOINT,
                HttpMethod.POST,
                entity,
                MAP_TYPE
        );
        assertThat(urlResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // GET /api/v1/users/me unauthenticated -> 401
        ResponseEntity<Map<String, Object>> meResponse = rest.exchange(
                ME_ENDPOINT,
                HttpMethod.GET,
                null,
                MAP_TYPE
        );
        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
