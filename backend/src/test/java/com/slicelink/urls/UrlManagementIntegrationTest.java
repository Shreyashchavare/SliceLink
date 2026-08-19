package com.slicelink.urls;

import static org.assertj.core.api.Assertions.assertThat;

import com.slicelink.auth.AuthenticationResponse;
import com.slicelink.auth.RegisterRequest;
import com.slicelink.users.User;
import com.slicelink.users.UserRepository;
import com.slicelink.users.UserStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Integration tests for Phase 6 URL Management endpoints:
 * listing, retrieving, updating destination, updating status, deleting,
 * ownership isolation, unauthenticated access protection, and Redis cache invalidation.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UrlManagementIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";
    private static final String URLS_ENDPOINT = "/api/v1/urls";

    private static final ParameterizedTypeReference<List<UrlResponse>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};
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
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory(httpClient));
    }

    private String registerAndGetToken(String emailPrefix) {
        String email = emailPrefix + System.nanoTime() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(email, "Password123!", "Management Tester");
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

    private UrlResponse createUrlViaApi(String token, String originalUrl) {
        CreateUrlRequest request = new CreateUrlRequest(originalUrl);
        HttpEntity<CreateUrlRequest> entity = new HttpEntity<>(request, authHeaders(token));
        ResponseEntity<UrlResponse> response = rest.postForEntity(URLS_ENDPOINT, entity, UrlResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    // ==================================================================
    // 1. List URLs (GET /api/v1/urls)
    // ==================================================================

    @Nested
    @DisplayName("List URLs (GET /api/v1/urls)")
    class ListUrlsTests {

        @Test
        @DisplayName("authenticated user lists only their own URLs in descending order")
        void listUrls_returnsOnlyAuthenticatedUserUrls() {
            String tokenUserA = registerAndGetToken("usera_list");
            String tokenUserB = registerAndGetToken("userb_list");

            UrlResponse urlA1 = createUrlViaApi(tokenUserA, "https://example.com/a1");
            UrlResponse urlA2 = createUrlViaApi(tokenUserA, "https://example.com/a2");
            UrlResponse urlB1 = createUrlViaApi(tokenUserB, "https://example.com/b1");

            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(tokenUserA));
            ResponseEntity<List<UrlResponse>> response = rest.exchange(
                    URLS_ENDPOINT,
                    HttpMethod.GET,
                    entity,
                    LIST_TYPE
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            List<UrlResponse> urls = response.getBody();
            assertThat(urls).isNotNull();
            assertThat(urls).extracting(UrlResponse::id).contains(urlA1.id(), urlA2.id()).doesNotContain(urlB1.id());
            assertThat(urls.get(0).id()).isEqualTo(urlA2.id()); // newest first
        }
    }

    // ==================================================================
    // 2. Get Single URL (GET /api/v1/urls/{id})
    // ==================================================================

    @Nested
    @DisplayName("Get Single URL (GET /api/v1/urls/{id})")
    class GetUrlTests {

        @Test
        @DisplayName("authenticated user retrieves their own URL by ID")
        void getUrl_ownUrl_returns200AndUrlResponse() {
            String token = registerAndGetToken("user_get_own");
            UrlResponse created = createUrlViaApi(token, "https://example.com/own-target");

            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
            ResponseEntity<UrlResponse> response = rest.exchange(
                    URLS_ENDPOINT + "/" + created.id(),
                    HttpMethod.GET,
                    entity,
                    UrlResponse.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(created.id());
            assertThat(response.getBody().originalUrl()).isEqualTo("https://example.com/own-target");
            assertThat(response.getBody().shortCode()).isEqualTo(created.shortCode());
            assertThat(response.getBody().status()).isEqualTo(UrlStatus.ACTIVE);
        }

        @Test
        @DisplayName("user cannot retrieve another user's URL (returns 404 URL_NOT_FOUND)")
        void getUrl_otherUsersUrl_returns404() {
            String tokenUserA = registerAndGetToken("usera_get");
            String tokenUserB = registerAndGetToken("userb_get");

            UrlResponse urlB = createUrlViaApi(tokenUserB, "https://example.com/secret-b");

            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(tokenUserA));
            ResponseEntity<Map<String, Object>> response = rest.exchange(
                    URLS_ENDPOINT + "/" + urlB.id(),
                    HttpMethod.GET,
                    entity,
                    MAP_TYPE
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("code")).isEqualTo("URL_NOT_FOUND");
        }
    }

    // ==================================================================
    // 3. Update Destination URL (PUT & PATCH /api/v1/urls/{id})
    // ==================================================================

    @Nested
    @DisplayName("Update Destination URL")
    class UpdateUrlTests {

        @Test
        @DisplayName("PUT updates originalUrl and invalidates redirect cache")
        void updateUrl_put_updatesDestinationAndInvalidatesCache() {
            String token = registerAndGetToken("user_update_put");
            UrlResponse created = createUrlViaApi(token, "https://example.com/old-destination");

            // 1. Trigger redirect to cache the old destination
            ResponseEntity<Void> redirect1 = rest.getForEntity("/" + created.shortCode(), Void.class);
            assertThat(redirect1.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(redirect1.getHeaders().getLocation()).isEqualTo(URI.create("https://example.com/old-destination"));

            // 2. Update destination URL via PUT
            String newTarget = "https://example.com/new-updated-destination";
            UpdateUrlRequest updateRequest = new UpdateUrlRequest(newTarget);
            HttpEntity<UpdateUrlRequest> updateEntity = new HttpEntity<>(updateRequest, authHeaders(token));

            ResponseEntity<UrlResponse> updateResponse = rest.exchange(
                    URLS_ENDPOINT + "/" + created.id(),
                    HttpMethod.PUT,
                    updateEntity,
                    UrlResponse.class
            );

            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updateResponse.getBody()).isNotNull();
            assertThat(updateResponse.getBody().originalUrl()).isEqualTo(newTarget);

            // 3. Trigger redirect again -> verify cache was invalidated and points to new destination
            ResponseEntity<Void> redirect2 = rest.getForEntity("/" + created.shortCode(), Void.class);
            assertThat(redirect2.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(redirect2.getHeaders().getLocation()).isEqualTo(URI.create(newTarget));
        }

        @Test
        @DisplayName("PATCH updates originalUrl successfully")
        void updateUrl_patch_updatesDestination() {
            String token = registerAndGetToken("user_update_patch");
            UrlResponse created = createUrlViaApi(token, "https://example.com/patch-old");

            String newTarget = "https://example.com/patch-new";
            UpdateUrlRequest updateRequest = new UpdateUrlRequest(newTarget);
            HttpEntity<UpdateUrlRequest> updateEntity = new HttpEntity<>(updateRequest, authHeaders(token));

            ResponseEntity<UrlResponse> updateResponse = rest.exchange(
                    URLS_ENDPOINT + "/" + created.id(),
                    HttpMethod.PATCH,
                    updateEntity,
                    UrlResponse.class
            );

            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updateResponse.getBody()).isNotNull();
            assertThat(updateResponse.getBody().originalUrl()).isEqualTo(newTarget);
        }

        @Test
        @DisplayName("updating another user's URL returns 404 URL_NOT_FOUND")
        void updateUrl_otherUsersUrl_returns404() {
            String tokenUserA = registerAndGetToken("usera_upd");
            String tokenUserB = registerAndGetToken("userb_upd");

            UrlResponse urlB = createUrlViaApi(tokenUserB, "https://example.com/target-b");

            UpdateUrlRequest updateRequest = new UpdateUrlRequest("https://example.com/hacked");
            HttpEntity<UpdateUrlRequest> entity = new HttpEntity<>(updateRequest, authHeaders(tokenUserA));

            ResponseEntity<Map<String, Object>> response = rest.exchange(
                    URLS_ENDPOINT + "/" + urlB.id(),
                    HttpMethod.PUT,
                    entity,
                    MAP_TYPE
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("code")).isEqualTo("URL_NOT_FOUND");
        }

        @Test
        @DisplayName("updating with invalid URL returns 400 VALIDATION_FAILED")
        void updateUrl_invalidPayload_returns400() {
            String token = registerAndGetToken("user_upd_invalid");
            UrlResponse created = createUrlViaApi(token, "https://example.com/valid");

            UpdateUrlRequest updateRequest = new UpdateUrlRequest("not-a-valid-url");
            HttpEntity<UpdateUrlRequest> entity = new HttpEntity<>(updateRequest, authHeaders(token));

            ResponseEntity<Map<String, Object>> response = rest.exchange(
                    URLS_ENDPOINT + "/" + created.id(),
                    HttpMethod.PUT,
                    entity,
                    MAP_TYPE
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("code")).isEqualTo("VALIDATION_FAILED");
        }
    }

    // ==================================================================
    // 4. Update Status (PATCH /api/v1/urls/{id}/status)
    // ==================================================================

    @Nested
    @DisplayName("Update Status (PATCH /api/v1/urls/{id}/status)")
    class UpdateStatusTests {

        @Test
        @DisplayName("disabling URL invalidates cache and returns 410 on public redirect")
        void updateStatus_disable_invalidatesCacheAndReturns410() {
            String token = registerAndGetToken("user_disable_test");
            String target = "https://example.com/to-be-disabled";
            UrlResponse created = createUrlViaApi(token, target);

            // 1. Populate redirect cache
            ResponseEntity<Void> redirect1 = rest.getForEntity("/" + created.shortCode(), Void.class);
            assertThat(redirect1.getStatusCode()).isEqualTo(HttpStatus.FOUND);

            // 2. Disable URL
            UpdateUrlStatusRequest statusRequest = new UpdateUrlStatusRequest(UrlStatus.DISABLED);
            HttpEntity<UpdateUrlStatusRequest> entity = new HttpEntity<>(statusRequest, authHeaders(token));

            ResponseEntity<UrlResponse> statusResponse = rest.exchange(
                    URLS_ENDPOINT + "/" + created.id() + "/status",
                    HttpMethod.PATCH,
                    entity,
                    UrlResponse.class
            );

            assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(statusResponse.getBody()).isNotNull();
            assertThat(statusResponse.getBody().status()).isEqualTo(UrlStatus.DISABLED);

            // 3. Public redirect must return 410 Gone with URL_DISABLED code
            ResponseEntity<Map<String, Object>> redirect2 = rest.exchange(
                    "/" + created.shortCode(),
                    HttpMethod.GET,
                    null,
                    MAP_TYPE
            );

            assertThat(redirect2.getStatusCode()).isEqualTo(HttpStatus.GONE);
            assertThat(redirect2.getBody()).isNotNull();
            assertThat(redirect2.getBody().get("code")).isEqualTo("URL_DISABLED");
        }

        @Test
        @DisplayName("re-enabling a disabled URL restores 302 redirect")
        void updateStatus_reEnable_restores302Redirect() {
            String token = registerAndGetToken("user_reenable_test");
            String target = "https://example.com/reenable-target";
            UrlResponse created = createUrlViaApi(token, target);

            // Disable
            HttpEntity<UpdateUrlStatusRequest> disableEntity = new HttpEntity<>(
                    new UpdateUrlStatusRequest(UrlStatus.DISABLED), authHeaders(token));
            rest.exchange(URLS_ENDPOINT + "/" + created.id() + "/status", HttpMethod.PATCH, disableEntity, UrlResponse.class);

            // Re-enable
            HttpEntity<UpdateUrlStatusRequest> enableEntity = new HttpEntity<>(
                    new UpdateUrlStatusRequest(UrlStatus.ACTIVE), authHeaders(token));
            ResponseEntity<UrlResponse> enableResponse = rest.exchange(
                    URLS_ENDPOINT + "/" + created.id() + "/status",
                    HttpMethod.PATCH,
                    enableEntity,
                    UrlResponse.class
            );

            assertThat(enableResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(enableResponse.getBody()).isNotNull();
            assertThat(enableResponse.getBody().status()).isEqualTo(UrlStatus.ACTIVE);

            // Public redirect succeeds with 302 Found
            ResponseEntity<Void> redirect = rest.getForEntity("/" + created.shortCode(), Void.class);
            assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(redirect.getHeaders().getLocation()).isEqualTo(URI.create(target));
        }

        @Test
        @DisplayName("disabling another user's URL returns 404 URL_NOT_FOUND")
        void updateStatus_otherUsersUrl_returns404() {
            String tokenUserA = registerAndGetToken("usera_dis");
            String tokenUserB = registerAndGetToken("userb_dis");

            UrlResponse urlB = createUrlViaApi(tokenUserB, "https://example.com/url-b");

            UpdateUrlStatusRequest statusRequest = new UpdateUrlStatusRequest(UrlStatus.DISABLED);
            HttpEntity<UpdateUrlStatusRequest> entity = new HttpEntity<>(statusRequest, authHeaders(tokenUserA));

            ResponseEntity<Map<String, Object>> response = rest.exchange(
                    URLS_ENDPOINT + "/" + urlB.id() + "/status",
                    HttpMethod.PATCH,
                    entity,
                    MAP_TYPE
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("code")).isEqualTo("URL_NOT_FOUND");
        }
    }

    // ==================================================================
    // 5. Delete URL (DELETE /api/v1/urls/{id})
    // ==================================================================

    @Nested
    @DisplayName("Delete URL (DELETE /api/v1/urls/{id})")
    class DeleteUrlTests {

        @Test
        @DisplayName("deleting own URL returns 204, invalidates cache, and returns 404 on redirect")
        void deleteUrl_ownUrl_returns204AndInvalidatesCache() {
            String token = registerAndGetToken("user_delete_test");
            String target = "https://example.com/to-be-deleted";
            UrlResponse created = createUrlViaApi(token, target);

            // 1. Populate redirect cache
            ResponseEntity<Void> redirect1 = rest.getForEntity("/" + created.shortCode(), Void.class);
            assertThat(redirect1.getStatusCode()).isEqualTo(HttpStatus.FOUND);

            // 2. Delete URL
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
            ResponseEntity<Void> deleteResponse = rest.exchange(
                    URLS_ENDPOINT + "/" + created.id(),
                    HttpMethod.DELETE,
                    entity,
                    Void.class
            );

            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            // 3. Subsequent GET /api/v1/urls/{id} returns 404
            ResponseEntity<Map<String, Object>> getResponse = rest.exchange(
                    URLS_ENDPOINT + "/" + created.id(),
                    HttpMethod.GET,
                    entity,
                    MAP_TYPE
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            // 4. Subsequent redirect returns 404 URL_NOT_FOUND (cache was evicted)
            ResponseEntity<Map<String, Object>> redirect2 = rest.exchange(
                    "/" + created.shortCode(),
                    HttpMethod.GET,
                    null,
                    MAP_TYPE
            );
            assertThat(redirect2.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(redirect2.getBody()).isNotNull();
            assertThat(redirect2.getBody().get("code")).isEqualTo("URL_NOT_FOUND");
        }

        @Test
        @DisplayName("deleting another user's URL returns 404 URL_NOT_FOUND")
        void deleteUrl_otherUsersUrl_returns404() {
            String tokenUserA = registerAndGetToken("usera_del");
            String tokenUserB = registerAndGetToken("userb_del");

            UrlResponse urlB = createUrlViaApi(tokenUserB, "https://example.com/safe-b");

            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(tokenUserA));
            ResponseEntity<Map<String, Object>> response = rest.exchange(
                    URLS_ENDPOINT + "/" + urlB.id(),
                    HttpMethod.DELETE,
                    entity,
                    MAP_TYPE
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("code")).isEqualTo("URL_NOT_FOUND");
        }
    }

    // ==================================================================
    // 6. Security & Error Handling
    // ==================================================================

    @Nested
    @DisplayName("Security & Error Handling")
    class SecurityAndErrorTests {

        @Test
        @DisplayName("unauthenticated requests to all management endpoints return 401 Unauthorized")
        void unauthenticatedRequests_return401() {
            // GET /api/v1/urls
            ResponseEntity<Map<String, Object>> listRes = rest.exchange(
                    URLS_ENDPOINT, HttpMethod.GET, null, MAP_TYPE);
            assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // GET /api/v1/urls/1
            ResponseEntity<Map<String, Object>> getRes = rest.exchange(
                    URLS_ENDPOINT + "/1", HttpMethod.GET, null, MAP_TYPE);
            assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // PUT /api/v1/urls/1
            HttpEntity<UpdateUrlRequest> putEntity = new HttpEntity<>(new UpdateUrlRequest("https://example.com"));
            ResponseEntity<Map<String, Object>> putRes = rest.exchange(
                    URLS_ENDPOINT + "/1", HttpMethod.PUT, putEntity, MAP_TYPE);
            assertThat(putRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // PATCH /api/v1/urls/1/status
            HttpEntity<UpdateUrlStatusRequest> statusEntity = new HttpEntity<>(new UpdateUrlStatusRequest(UrlStatus.DISABLED));
            ResponseEntity<Map<String, Object>> patchRes = rest.exchange(
                    URLS_ENDPOINT + "/1/status", HttpMethod.PATCH, statusEntity, MAP_TYPE);
            assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            // DELETE /api/v1/urls/1
            ResponseEntity<Map<String, Object>> delRes = rest.exchange(
                    URLS_ENDPOINT + "/1", HttpMethod.DELETE, null, MAP_TYPE);
            assertThat(delRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("non-existent URL ID returns 404 URL_NOT_FOUND across all endpoints")
        void nonExistentUrl_returns404() {
            String token = registerAndGetToken("user_404_test");
            HttpHeaders headers = authHeaders(token);

            // GET
            ResponseEntity<Map<String, Object>> getRes = rest.exchange(
                    URLS_ENDPOINT + "/999999", HttpMethod.GET, new HttpEntity<>(headers), MAP_TYPE);
            assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            // PUT
            ResponseEntity<Map<String, Object>> putRes = rest.exchange(
                    URLS_ENDPOINT + "/999999", HttpMethod.PUT,
                    new HttpEntity<>(new UpdateUrlRequest("https://example.com"), headers), MAP_TYPE);
            assertThat(putRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            // PATCH status
            ResponseEntity<Map<String, Object>> patchRes = rest.exchange(
                    URLS_ENDPOINT + "/999999/status", HttpMethod.PATCH,
                    new HttpEntity<>(new UpdateUrlStatusRequest(UrlStatus.DISABLED), headers), MAP_TYPE);
            assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            // DELETE
            ResponseEntity<Map<String, Object>> delRes = rest.exchange(
                    URLS_ENDPOINT + "/999999", HttpMethod.DELETE, new HttpEntity<>(headers), MAP_TYPE);
            assertThat(delRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
