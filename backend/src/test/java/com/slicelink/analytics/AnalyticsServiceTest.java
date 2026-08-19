package com.slicelink.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.slicelink.shared.ApiException;
import com.slicelink.urls.Url;
import com.slicelink.urls.UrlRepository;
import com.slicelink.urls.UrlStatus;
import com.slicelink.users.User;
import com.slicelink.users.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class AnalyticsServiceTest {

    private ClickEventRepository clickEventRepository;
    private UrlRepository urlRepository;
    private AnalyticsService analyticsService;

    private User testUser;
    private Url testUrl;

    @BeforeEach
    void setUp() {
        clickEventRepository = mock(ClickEventRepository.class);
        urlRepository = mock(UrlRepository.class);
        analyticsService = new AnalyticsService(clickEventRepository, urlRepository);

        testUser = new User("user@example.com", "hash", "User", UserStatus.ACTIVE);
        testUrl = new Url(100L, testUser, "https://example.com/target", "3D7gK", UrlStatus.ACTIVE);
    }

    @Nested
    @DisplayName("Record Click (Idempotency)")
    class RecordClickTests {

        @Test
        @DisplayName("recordClick persists new ClickEventEntity when eventId does not exist")
        void recordClick_newUniqueEvent_persistsEntity() {
            String eventId = UUID.randomUUID().toString();
            Instant now = Instant.now();
            ClickEvent event = new ClickEvent(eventId, 100L, "3D7gK", 1L, now);

            when(clickEventRepository.existsByEventId(eventId)).thenReturn(false);

            analyticsService.recordClick(event);

            verify(clickEventRepository).save(any(ClickEventEntity.class));
        }

        @Test
        @DisplayName("recordClick ignores duplicate eventId idempotently without saving")
        void recordClick_duplicateEvent_doesNotSave() {
            String eventId = "dup-123";
            ClickEvent event = new ClickEvent(eventId, 100L, "3D7gK", 1L, Instant.now());

            when(clickEventRepository.existsByEventId(eventId)).thenReturn(true);

            analyticsService.recordClick(event);

            verify(clickEventRepository, never()).save(any(ClickEventEntity.class));
        }

        @Test
        @DisplayName("recordClick handles DataIntegrityViolationException safely without throwing")
        void recordClick_dataIntegrityViolation_handledGracefully() {
            String eventId = "race-condition-event";
            ClickEvent event = new ClickEvent(eventId, 100L, "3D7gK", 1L, Instant.now());

            when(clickEventRepository.existsByEventId(eventId)).thenReturn(false);
            when(clickEventRepository.save(any(ClickEventEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

            analyticsService.recordClick(event);
        }
    }

    @Nested
    @DisplayName("Get Analytics (Ownership & Retrieval)")
    class GetAnalyticsTests {

        @Test
        @DisplayName("getAnalytics returns total clicks and recent clicks for authenticated owner")
        void getAnalytics_ownUrl_returnsAnalyticsResponse() {
            Instant clickTime = Instant.now();
            ClickEventEntity entity = new ClickEventEntity("e1", 100L, "3D7gK", 1L, clickTime);

            when(urlRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testUrl));
            when(clickEventRepository.countByUrlId(100L)).thenReturn(42L);
            when(clickEventRepository.findTop10ByUrlIdOrderByOccurredAtDesc(100L)).thenReturn(List.of(entity));

            UrlAnalyticsResponse response = analyticsService.getAnalytics(100L, 1L);

            assertThat(response.urlId()).isEqualTo(100L);
            assertThat(response.shortCode()).isEqualTo("3D7gK");
            assertThat(response.totalClicks()).isEqualTo(42L);
            assertThat(response.recentClicks()).containsExactly(clickTime);
        }

        @Test
        @DisplayName("getAnalytics throws 404 URL_NOT_FOUND when URL does not exist or user is not owner")
        void getAnalytics_otherUsersUrl_throws404() {
            when(urlRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> analyticsService.getAnalytics(100L, 2L))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(apiEx.getCode()).isEqualTo("URL_NOT_FOUND");
                    });
        }
    }
}
