package com.slicelink.urls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.slicelink.shared.ApiException;
import com.slicelink.users.User;
import com.slicelink.users.UserRepository;
import com.slicelink.users.UserStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class UrlServiceManagementTest {

    private UrlRepository urlRepository;
    private UserRepository userRepository;
    private UrlIdGenerator idGenerator;
    private UrlCacheService urlCacheService;
    private com.slicelink.analytics.ClickEventProducer clickEventProducer;
    private UrlService urlService;

    private User testUser;
    private Url testUrl;

    @BeforeEach
    void setUp() {
        urlRepository = mock(UrlRepository.class);
        userRepository = mock(UserRepository.class);
        idGenerator = mock(UrlIdGenerator.class);
        urlCacheService = mock(UrlCacheService.class);
        clickEventProducer = mock(com.slicelink.analytics.ClickEventProducer.class);

        urlService = new UrlService(urlRepository, userRepository, idGenerator, urlCacheService, clickEventProducer);

        testUser = new User("owner@example.com", "hash", "Owner", UserStatus.ACTIVE);
        testUrl = new Url(100L, testUser, "https://example.com/target", "3D7gK", UrlStatus.ACTIVE);
    }

    @Nested
    @DisplayName("List URLs by Owner")
    class ListUrlsTests {

        @Test
        @DisplayName("listByOwner returns URLs ordered newest first")
        void listByOwner_returnsUrls() {
            when(urlRepository.findAllByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(testUrl));

            List<Url> results = urlService.listByOwner(1L);

            assertThat(results).containsExactly(testUrl);
            verify(urlRepository).findAllByUserIdOrderByCreatedAtDesc(1L);
        }
    }

    @Nested
    @DisplayName("Get URL by ID and Owner")
    class GetUrlTests {

        @Test
        @DisplayName("getByIdAndOwner returns URL when found")
        void getByIdAndOwner_whenFound_returnsUrl() {
            when(urlRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testUrl));

            Url result = urlService.getByIdAndOwner(100L, 1L);

            assertThat(result).isSameAs(testUrl);
        }

        @Test
        @DisplayName("getByIdAndOwner throws 404 URL_NOT_FOUND when not found or wrong user")
        void getByIdAndOwner_whenNotFound_throws404() {
            when(urlRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> urlService.getByIdAndOwner(100L, 2L))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> {
                        ApiException apiEx = (ApiException) ex;
                        assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(apiEx.getCode()).isEqualTo("URL_NOT_FOUND");
                    });
        }
    }

    @Nested
    @DisplayName("Update Destination URL")
    class UpdateOriginalUrlTests {

        @Test
        @DisplayName("updateOriginalUrl updates destination, evicts cache, and persists")
        void updateOriginalUrl_whenChanged_updatesAndEvictsCache() {
            when(urlRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testUrl));
            when(urlRepository.save(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

            Url updated = urlService.updateOriginalUrl(100L, 1L, "https://example.com/new-dest");

            assertThat(updated.getOriginalUrl()).isEqualTo("https://example.com/new-dest");
            verify(urlCacheService).evict("3D7gK");
            verify(urlRepository).save(testUrl);
        }

        @Test
        @DisplayName("updateOriginalUrl does not evict or re-save when URL unchanged")
        void updateOriginalUrl_whenUnchanged_doesNotEvict() {
            when(urlRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testUrl));

            Url updated = urlService.updateOriginalUrl(100L, 1L, "https://example.com/target");

            assertThat(updated.getOriginalUrl()).isEqualTo("https://example.com/target");
            verify(urlCacheService, never()).evict(any());
            verify(urlRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Update Status")
    class UpdateStatusTests {

        @Test
        @DisplayName("updateStatus toggles status, evicts cache, and persists")
        void updateStatus_whenChanged_updatesAndEvictsCache() {
            when(urlRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testUrl));
            when(urlRepository.save(any(Url.class))).thenAnswer(inv -> inv.getArgument(0));

            Url updated = urlService.updateStatus(100L, 1L, UrlStatus.DISABLED);

            assertThat(updated.getStatus()).isEqualTo(UrlStatus.DISABLED);
            verify(urlCacheService).evict("3D7gK");
            verify(urlRepository).save(testUrl);
        }

        @Test
        @DisplayName("updateStatus does not evict or re-save when status unchanged")
        void updateStatus_whenUnchanged_doesNotEvict() {
            when(urlRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testUrl));

            Url updated = urlService.updateStatus(100L, 1L, UrlStatus.ACTIVE);

            assertThat(updated.getStatus()).isEqualTo(UrlStatus.ACTIVE);
            verify(urlCacheService, never()).evict(any());
            verify(urlRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Delete URL")
    class DeleteUrlTests {

        @Test
        @DisplayName("delete evicts cache and removes URL from repository")
        void delete_evictsCacheAndDeletes() {
            when(urlRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(testUrl));

            urlService.delete(100L, 1L);

            verify(urlCacheService).evict("3D7gK");
            verify(urlRepository).delete(testUrl);
        }
    }
}
