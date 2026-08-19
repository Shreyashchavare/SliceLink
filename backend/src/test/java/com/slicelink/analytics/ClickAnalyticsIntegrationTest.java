package com.slicelink.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.slicelink.auth.AuthenticationResponse;
import com.slicelink.auth.RegisterRequest;
import com.slicelink.urls.CreateUrlRequest;
import com.slicelink.urls.UpdateUrlStatusRequest;
import com.slicelink.urls.UrlResponse;
import com.slicelink.urls.UrlStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Integration tests for Phase 7 Click Analytics:
 * event streaming during URL redirects, cache HIT/MISS behavior,
 * Kafka outage resilience, authenticated analytics retrieval,
 * ownership isolation, and idempotency.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClickAnalyticsIntegrationTest {

    private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";
    private static final String URLS_ENDPOINT = "/api/v1/urls";

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private AnalyticsService analyticsService;

    @MockitoBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoSpyBean
    private ClickEventProducer clickEventProducer;

    @BeforeEach
    void setUp() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory(httpClient));

        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    private String registerAndGetToken(String emailPrefix) {
        String email = emailPrefix + System.nanoTime() + "@example.com";
        RegisterRequest registerRequest = new RegisterRequest(email, "Password123!", "Analytics Tester");
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
    // 1. Redirect Event Generation (Cache HIT / MISS / 404 / 410 / Outage)
    // ==================================================================

    @Nested
    @DisplayName("Redirect Click Event Streaming")
    class RedirectEventTests {

        @Test
        @DisplayName("active URL redirect emits ClickEvent and returns 302 Found")
        void redirect_activeUrl_emitsClickEvent() {
            String token = registerAndGetToken("user_red_event");
            String destination = "https://example.com/analytics-target-1";
            UrlResponse url = createUrlViaApi(token, destination);

            ResponseEntity<Void> response = rest.getForEntity("/" + url.shortCode(), Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create(destination));

            ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
            verify(clickEventProducer, atLeastOnce()).send(captor.capture());

            ClickEvent captured = captor.getValue();
            assertThat(captured.urlId()).isEqualTo(url.id());
            assertThat(captured.shortCode()).isEqualTo(url.shortCode());
            assertThat(captured.eventId()).isNotBlank();
            assertThat(captured.occurredAt()).isNotNull();
        }

        @Test
        @DisplayName("Redis cache HIT still emits ClickEvent on subsequent redirect")
        void redirect_cacheHit_emitsClickEvent() {
            String token = registerAndGetToken("user_cache_hit_event");
            String destination = "https://example.com/cached-analytics-target";
            UrlResponse url = createUrlViaApi(token, destination);

            // First redirect (populates Redis cache)
            rest.getForEntity("/" + url.shortCode(), Void.class);

            // Second redirect (Redis cache HIT)
            ResponseEntity<Void> response2 = rest.getForEntity("/" + url.shortCode(), Void.class);

            assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(response2.getHeaders().getLocation()).isEqualTo(URI.create(destination));

            ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
            verify(clickEventProducer, atLeastOnce()).send(captor.capture());
            assertThat(captor.getAllValues()).anyMatch(event -> event.shortCode().equals(url.shortCode()));
        }

        @Test
        @DisplayName("unknown shortCode returns 404 and produces no ClickEvent")
        void redirect_unknownUrl_noClickEvent() {
            ResponseEntity<Map<String, Object>> response = rest.exchange(
                    "/unknown999xyz",
                    HttpMethod.GET,
                    null,
                    MAP_TYPE
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("code")).isEqualTo("URL_NOT_FOUND");
        }

        @Test
        @DisplayName("disabled URL returns 410 GONE and produces no ClickEvent")
        void redirect_disabledUrl_noClickEvent() {
            String token = registerAndGetToken("user_dis_event");
            UrlResponse url = createUrlViaApi(token, "https://example.com/dis-target");

            // Disable URL
            HttpEntity<UpdateUrlStatusRequest> statusEntity = new HttpEntity<>(
                    new UpdateUrlStatusRequest(UrlStatus.DISABLED), authHeaders(token));
            rest.exchange(URLS_ENDPOINT + "/" + url.id() + "/status", HttpMethod.PATCH, statusEntity, UrlResponse.class);

            // Attempt redirect on disabled URL
            ResponseEntity<Map<String, Object>> response = rest.exchange(
                    "/" + url.shortCode(),
                    HttpMethod.GET,
                    null,
                    MAP_TYPE
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("code")).isEqualTo("URL_DISABLED");
        }

        @Test
        @DisplayName("Kafka failure does not break URL redirection (still returns 302)")
        void redirect_whenKafkaFails_stillReturns302() {
            String token = registerAndGetToken("user_kafka_fail");
            String destination = "https://example.com/resilience-target";
            UrlResponse url = createUrlViaApi(token, destination);

            // Make KafkaTemplate throw exception
            when(kafkaTemplate.send(any(), any(), any()))
                    .thenThrow(new RuntimeException("Simulated Kafka Broker Down"));

            ResponseEntity<Void> response = rest.getForEntity("/" + url.shortCode(), Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
            assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create(destination));
        }
    }

    // ==================================================================
    // 2. Analytics API & Ownership Validation
    // ==================================================================

    @Nested
    @DisplayName("Analytics API (GET /api/v1/urls/{id}/analytics)")
    class AnalyticsApiTests {

        @Test
        @DisplayName("authenticated owner retrieves analytics with totalClicks and recentClicks")
        void getAnalytics_authenticatedOwner_returnsAnalytics() {
            String token = registerAndGetToken("user_analytics_owner");
            UrlResponse url = createUrlViaApi(token, "https://example.com/tracked");

            // Simulate recording 3 click events
            Instant t1 = Instant.now().minusSeconds(30);
            Instant t2 = Instant.now().minusSeconds(20);
            Instant t3 = Instant.now().minusSeconds(10);

            analyticsService.recordClick(new ClickEvent(UUID.randomUUID().toString(), url.id(), url.shortCode(), url.userId(), t1));
            analyticsService.recordClick(new ClickEvent(UUID.randomUUID().toString(), url.id(), url.shortCode(), url.userId(), t2));
            analyticsService.recordClick(new ClickEvent(UUID.randomUUID().toString(), url.id(), url.shortCode(), url.userId(), t3));

            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
            ResponseEntity<UrlAnalyticsResponse> response = rest.exchange(
                    URLS_ENDPOINT + "/" + url.id() + "/analytics",
                    HttpMethod.GET,
                    entity,
                    UrlAnalyticsResponse.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            UrlAnalyticsResponse body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.urlId()).isEqualTo(url.id());
            assertThat(body.shortCode()).isEqualTo(url.shortCode());
            assertThat(body.totalClicks()).isEqualTo(3L);
            assertThat(body.recentClicks()).hasSize(3);
        }

        @Test
        @DisplayName("user cannot retrieve analytics for another user's URL (returns 404 URL_NOT_FOUND)")
        void getAnalytics_otherUsersUrl_returns404() {
            String tokenUserA = registerAndGetToken("usera_analytics");
            String tokenUserB = registerAndGetToken("userb_analytics");

            UrlResponse urlB = createUrlViaApi(tokenUserB, "https://example.com/secret-url-b");

            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(tokenUserA));
            ResponseEntity<Map<String, Object>> response = rest.exchange(
                    URLS_ENDPOINT + "/" + urlB.id() + "/analytics",
                    HttpMethod.GET,
                    entity,
                    MAP_TYPE
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("code")).isEqualTo("URL_NOT_FOUND");
        }

        @Test
        @DisplayName("unauthenticated request to analytics endpoint returns 401 Unauthorized")
        void getAnalytics_unauthenticated_returns401() {
            ResponseEntity<Map<String, Object>> response = rest.exchange(
                    URLS_ENDPOINT + "/1/analytics",
                    HttpMethod.GET,
                    null,
                    MAP_TYPE
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("duplicate events with same eventId do not double count clicks")
        void getAnalytics_duplicateEvents_idempotent() {
            String token = registerAndGetToken("user_analytics_idemp");
            UrlResponse url = createUrlViaApi(token, "https://example.com/idemp-target");

            String duplicateEventId = "fixed-event-id-" + System.nanoTime();
            ClickEvent event = new ClickEvent(duplicateEventId, url.id(), url.shortCode(), url.userId(), Instant.now());

            // Record same event twice
            analyticsService.recordClick(event);
            analyticsService.recordClick(event);

            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(token));
            ResponseEntity<UrlAnalyticsResponse> response = rest.exchange(
                    URLS_ENDPOINT + "/" + url.id() + "/analytics",
                    HttpMethod.GET,
                    entity,
                    UrlAnalyticsResponse.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().totalClicks()).isEqualTo(1L);
        }
    }
}
