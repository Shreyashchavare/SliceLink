package com.slicelink.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.slicelink.users.UserResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * HTTP-layer integration tests for the Phase 2 authentication flow.
 *
 * <p>Uses a real Spring context with the H2 in-memory database, Flyway
 * migrations, and the full Spring Security filter chain.
 *
 * <p>Stateful test groups use AFTER_EACH_TEST_METHOD so that database state
 * from one test method cannot affect another test method.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticationIntegrationTest {

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String REFRESH_URL = "/api/v1/auth/refresh";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";
    private static final String ME_URL = "/api/v1/users/me";

    static final String EMAIL = "alice@example.com";
    static final String PASSWORD = "SecurePass123!";
    static final String NAME = "Alice";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate rest;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    static Map<String, Object> registerBody() {
        return Map.of(
                "email", EMAIL,
                "password", PASSWORD,
                "name", NAME
        );
    }

    static Map<String, Object> registerBody(String email) {
        return Map.of(
                "email", email,
                "password", PASSWORD,
                "name", NAME
        );
    }

    static Map<String, Object> loginBody() {
        return Map.of(
                "email", EMAIL,
                "password", PASSWORD
        );
    }

    static Map<String, Object> loginBody(String email, String password) {
        return Map.of(
                "email", email,
                "password", password
        );
    }

    static Map<String, Object> refreshBody(String refreshToken) {
        return Map.of("refreshToken", refreshToken);
    }

    HttpHeaders bearerHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    AuthenticationResponse registerAlice() {
        ResponseEntity<AuthenticationResponse> response =
                rest.postForEntity(
                        REGISTER_URL,
                        registerBody(),
                        AuthenticationResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        AuthenticationResponse body = response.getBody();
        assertThat(body).isNotNull();

        return body;
    }

    // ==================================================================
    // Registration
    // ==================================================================

@Nested
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class Registration {

        @Test
        void successfulRegistrationReturns201WithTokens() {
            String email = "registration-success@example.com";

            ResponseEntity<AuthenticationResponse> response =
                    rest.postForEntity(
                            REGISTER_URL,
                            registerBody(email),
                            AuthenticationResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            AuthenticationResponse body = response.getBody();

            assertThat(body).isNotNull();
            assertThat(body.accessToken()).isNotBlank();
            assertThat(body.refreshToken()).isNotBlank();
            assertThat(body.tokenType()).isEqualTo("Bearer");
            assertThat(body.expiresIn()).isPositive();

            assertThat(body.user()).isNotNull();
            assertThat(body.user().email()).isEqualTo(email);
            assertThat(body.user().name()).isEqualTo(NAME);
        }

        @Test
        void duplicateEmailReturns409() {

        ResponseEntity<AuthenticationResponse> first =
                rest.postForEntity(
                        REGISTER_URL,
                        registerBody(),
                        AuthenticationResponse.class);

        assertThat(first.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map<String, Object>> duplicate =
        rest.exchange(
                        REGISTER_URL,
                        HttpMethod.POST,
                        new HttpEntity<>(registerBody()),
                        MAP_TYPE);

        assertThat(duplicate.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(duplicate.getBody())
                .containsEntry(
                        "code",
                        "EMAIL_ALREADY_REGISTERED");
        }

        @Test
        void shortPasswordReturns400() {
            Map<String, Object> bad = Map.of(
                    "email", "bob@example.com",
                    "password", "short1",
                    "name", "Bob");

            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            REGISTER_URL,
                            HttpMethod.POST,
                            new HttpEntity<>(bad),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody())
                    .containsEntry("code", "VALIDATION_FAILED");
        }

        @Test
        void missingEmailFieldReturns400() {
            Map<String, Object> bad = Map.of(
                    "password", PASSWORD,
                    "name", NAME);

            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            REGISTER_URL,
                            HttpMethod.POST,
                            new HttpEntity<>(bad),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void invalidEmailFormatReturns400() {
            Map<String, Object> bad = Map.of(
                    "email", "not-an-email",
                    "password", PASSWORD,
                    "name", NAME);

            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            REGISTER_URL,
                            HttpMethod.POST,
                            new HttpEntity<>(bad),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void passwordWithoutDigitReturns400() {
            Map<String, Object> bad = Map.of(
                    "email", "carol@example.com",
                    "password", "OnlyLettersPassword",
                    "name", "Carol");

            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            REGISTER_URL,
                            HttpMethod.POST,
                            new HttpEntity<>(bad),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ==================================================================
    // Login
    // ==================================================================

    @Nested
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    class Login {

        @BeforeEach
        void setup() {
            registerAlice();
        }

        @Test
        void successfulLoginReturnsTokens() {
            ResponseEntity<AuthenticationResponse> response =
                    rest.postForEntity(
                            LOGIN_URL,
                            loginBody(),
                            AuthenticationResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            AuthenticationResponse body = response.getBody();

            assertThat(body).isNotNull();
            assertThat(body.accessToken()).isNotBlank();
            assertThat(body.refreshToken()).isNotBlank();
            assertThat(body.tokenType()).isEqualTo("Bearer");
            assertThat(body.expiresIn()).isPositive();
        }

        @Test
        void wrongPasswordReturns401() {
            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            LOGIN_URL,
                            HttpMethod.POST,
                            new HttpEntity<>(
                                    loginBody(EMAIL, "WrongPassword1!")),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody())
                    .containsEntry("code", "INVALID_CREDENTIALS");
        }

        @Test
        void unknownEmailReturns401() {
            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            LOGIN_URL,
                            HttpMethod.POST,
                            new HttpEntity<>(
                                    loginBody("nobody@example.com", PASSWORD)),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody())
                    .containsEntry("code", "INVALID_CREDENTIALS");
        }

        @Test
        void emailIsCaseInsensitiveOnLogin() {
            ResponseEntity<AuthenticationResponse> response =
                    rest.postForEntity(
                            LOGIN_URL,
                            loginBody(EMAIL.toUpperCase(), PASSWORD),
                            AuthenticationResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ==================================================================
    // Current-user endpoint
    // ==================================================================

    @Nested
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    class CurrentUser {

        private AuthenticationResponse tokens;

        @BeforeEach
        void setup() {
            tokens = registerAlice();
        }

        @Test
        void unauthenticatedRequestReturns401() {
            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            ME_URL,
                            HttpMethod.GET,
                            new HttpEntity<>(new HttpHeaders()),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody())
                    .containsEntry("code", "UNAUTHORIZED");
        }

        @Test
        void validTokenReturns200WithUserData() {
            ResponseEntity<UserResponse> response =
                    rest.exchange(
                            ME_URL,
                            HttpMethod.GET,
                            new HttpEntity<>(
                                    bearerHeaders(tokens.accessToken())),
                            UserResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            UserResponse user = response.getBody();

            assertThat(user).isNotNull();
            assertThat(user.email()).isEqualTo(EMAIL);
            assertThat(user.name()).isEqualTo(NAME);
            assertThat(user.id()).isPositive();
            assertThat(user.status()).isNotNull();
        }

        @Test
        void responseDoesNotContainPasswordHash() {
            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            ME_URL,
                            HttpMethod.GET,
                            new HttpEntity<>(
                                    bearerHeaders(tokens.accessToken())),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Map<String, Object> body = response.getBody();

            assertThat(body).isNotNull();
            assertThat(body).doesNotContainKey("passwordHash");
            assertThat(body).doesNotContainKey("password_hash");
            assertThat(body).doesNotContainKey("password");
        }

        @Test
        void malformedTokenReturns401() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer this.is.not.a.jwt");

            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            ME_URL,
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void refreshTokenRejectedAsAccessToken() {
            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            ME_URL,
                            HttpMethod.GET,
                            new HttpEntity<>(
                                    bearerHeaders(tokens.refreshToken())),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==================================================================
    // Refresh-token rotation
    // ==================================================================

    @Nested
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    class RefreshTokenRotation {

        private AuthenticationResponse initial;

        @BeforeEach
        void setup() {
            initial = registerAlice();
        }

        @Test
        void refreshReturnsNewTokens() {
            ResponseEntity<AuthenticationResponse> response =
                    rest.postForEntity(
                            REFRESH_URL,
                            refreshBody(initial.refreshToken()),
                            AuthenticationResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            AuthenticationResponse rotated = response.getBody();

            assertThat(rotated).isNotNull();
            assertThat(rotated.accessToken())
                    .isNotBlank()
                    .isNotEqualTo(initial.accessToken());
            assertThat(rotated.refreshToken())
                    .isNotBlank()
                    .isNotEqualTo(initial.refreshToken());
            assertThat(rotated.expiresIn()).isPositive();
        }

        @Test
        void refreshedAccessTokenGrantsAccess() {
            AuthenticationResponse rotated =
                    rest.postForEntity(
                                    REFRESH_URL,
                                    refreshBody(initial.refreshToken()),
                                    AuthenticationResponse.class)
                            .getBody();

            assertThat(rotated).isNotNull();

            ResponseEntity<UserResponse> meResponse =
                    rest.exchange(
                            ME_URL,
                            HttpMethod.GET,
                            new HttpEntity<>(
                                    bearerHeaders(rotated.accessToken())),
                            UserResponse.class);

            assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(meResponse.getBody()).isNotNull();
            assertThat(meResponse.getBody().email()).isEqualTo(EMAIL);
        }

        @Test
        void reusingOldRefreshTokenAfterRotationReturns401() {
            rest.postForEntity(
                    REFRESH_URL,
                    refreshBody(initial.refreshToken()),
                    AuthenticationResponse.class);

            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            REFRESH_URL,
                            HttpMethod.POST,
                            new HttpEntity<>(
                                    refreshBody(initial.refreshToken())),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody())
                    .containsEntry("code", "INVALID_REFRESH_TOKEN");
        }

        @Test
        void invalidRefreshTokenReturns401() {
            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            REFRESH_URL,
                            HttpMethod.POST,
                            new HttpEntity<>(
                                    refreshBody("not.a.real.token")),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==================================================================
    // Logout / revocation
    // ==================================================================

    @Nested
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    class Logout {

        private AuthenticationResponse initial;

        @BeforeEach
        void setup() {
            initial = registerAlice();
        }

        @Test
        void logoutReturns204() {
            ResponseEntity<Void> response =
                    rest.exchange(
                            LOGOUT_URL,
                            HttpMethod.POST,
                            new HttpEntity<>(
                                    refreshBody(initial.refreshToken())),
                            Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        void reusingLoggedOutRefreshTokenReturns401() {
            rest.exchange(
                    LOGOUT_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(
                            refreshBody(initial.refreshToken())),
                    Void.class);

            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            REFRESH_URL,
                            HttpMethod.POST,
                            new HttpEntity<>(
                                    refreshBody(initial.refreshToken())),
                            MAP_TYPE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody())
                    .containsEntry("code", "INVALID_REFRESH_TOKEN");
        }

        @Test
        void accessTokenRemainsValidAfterLogout() {
            String accessToken = initial.accessToken();

            rest.exchange(
                    LOGOUT_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(
                            refreshBody(initial.refreshToken())),
                    Void.class);

            ResponseEntity<UserResponse> meResponse =
                    rest.exchange(
                            ME_URL,
                            HttpMethod.GET,
                            new HttpEntity<>(
                                    bearerHeaders(accessToken)),
                            UserResponse.class);

            // Access tokens are not revoked on logout.
            // Their short TTL provides the revocation mechanism.
            assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void logoutIsIdempotentForRevokedToken() {
            rest.exchange(
                    LOGOUT_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(
                            refreshBody(initial.refreshToken())),
                    Void.class);

            // Second logout with the same revoked token is silent.
            ResponseEntity<Void> again =
                    rest.exchange(
                            LOGOUT_URL,
                            HttpMethod.POST,
                            new HttpEntity<>(
                                    refreshBody(initial.refreshToken())),
                            Void.class);

            assertThat(again.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    // ==================================================================
    // Expired token
    // ==================================================================

    @Nested
    @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
    class ExpiredToken {

        @Autowired
        private JwtProperties jwtProperties;

        @Test
        void expiredAccessTokenReturns401() {
            Clock pastClock =
                    Clock.fixed(
                            Instant.now().minus(Duration.ofDays(1)),
                            ZoneOffset.UTC);

            JwtTokenService pastTokens =
                    new JwtTokenService(jwtProperties, pastClock);

            AuthenticationResponse reg =
        rest.postForEntity(
                        REGISTER_URL,
                        registerBody(),
                        AuthenticationResponse.class)
                .getBody();

                assertThat(reg).isNotNull();
                assertThat(reg.user()).isNotNull();

                com.slicelink.users.User stub =
                        new com.slicelink.users.User(
                                EMAIL,
                                "hash",
                                NAME,
                                com.slicelink.users.UserStatus.ACTIVE);

                ReflectionTestUtils.setField(
                        stub,
                        "id",
                        reg.user().id());

            String expiredToken =
                    pastTokens.issueAccessToken(stub).value();

            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            ME_URL,
                            HttpMethod.GET,
                            new HttpEntity<>(
                                    bearerHeaders(expiredToken)),
                            MAP_TYPE);

            assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ==================================================================
    // Error response shape
    // ==================================================================

    @Nested
    class ErrorResponseShape {

        @Test
        void errorResponseContainsRequiredFields() {
            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            ME_URL,
                            HttpMethod.GET,
                            new HttpEntity<>(new HttpHeaders()),
                            MAP_TYPE);

            assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);

            Map<String, Object> body = response.getBody();

            assertThat(body).isNotNull();
            assertThat(body).containsKey("timestamp");
            assertThat(body).containsKey("status");
            assertThat(body).containsKey("code");
            assertThat(body).containsKey("message");
            assertThat(body).containsKey("path");
            assertThat(body).containsKey("requestId");
        }

        @Test
        void requestIdHeaderIsEchoedInResponse() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Request-ID", "test-req-id-123");

            ResponseEntity<Map<String, Object>> response =
                    rest.exchange(
                            ME_URL,
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            MAP_TYPE);

            assertThat(response.getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);

            assertThat(response.getHeaders().getFirst("X-Request-ID"))
                    .isEqualTo("test-req-id-123");

            assertThat(response.getBody())
                    .containsEntry("requestId", "test-req-id-123");
        }
    }
}