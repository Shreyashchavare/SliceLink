package com.slicelink.urls;

import com.slicelink.analytics.ClickEvent;
import com.slicelink.analytics.ClickEventProducer;
import com.slicelink.shared.ApiException;
import com.slicelink.users.User;
import com.slicelink.users.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for URL creation and management.
 *
 * <h2>Short-code generation</h2>
 * <ol>
 *   <li>Ask {@link UrlIdGenerator} for a new time-ordered ID.</li>
 *   <li>Encode the ID with {@link Base62}.</li>
 *   <li>Check the database for a collision (should be extremely rare because
 *       the ID generator produces monotonically increasing values).</li>
 *   <li>Retry up to {@value #MAX_RETRIES} times if a collision occurs.</li>
 * </ol>
 *
 * <p>Collisions are only possible if the ID generator produces the same value
 * twice (e.g. after a JVM restart with a time skew). The retry loop adds a
 * safety net without making the common path more expensive.
 */
@Service
public class UrlService {

    /** Maximum collision-retry attempts before raising an internal error. */
    private static final int MAX_RETRIES = 5;

    private final UrlRepository      urlRepository;
    private final UserRepository     userRepository;
    private final UrlIdGenerator     idGenerator;
    private final UrlCacheService    urlCacheService;
    private final ClickEventProducer clickEventProducer;

    public UrlService(UrlRepository urlRepository,
                      UserRepository userRepository,
                      UrlIdGenerator idGenerator,
                      UrlCacheService urlCacheService,
                      ClickEventProducer clickEventProducer) {
        this.urlRepository      = urlRepository;
        this.userRepository     = userRepository;
        this.idGenerator        = idGenerator;
        this.urlCacheService    = urlCacheService;
        this.clickEventProducer = clickEventProducer;
    }

    /**
     * Creates and persists a new shortened URL for the given owner.
     *
     * @param ownerId     ID of the authenticated user making the request
     * @param originalUrl the long URL to shorten
     * @return the persisted {@link Url} record
     */
    @Transactional
    public Url create(Long ownerId, String originalUrl) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED",
                        "Authentication is required."));

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            long   id        = idGenerator.nextId();
            String shortCode = Base62.encode(id);

            if (!urlRepository.existsByShortCode(shortCode)) {
                Url url = new Url(id, owner, originalUrl, shortCode, UrlStatus.ACTIVE);
                return urlRepository.save(url);
            }
            // Collision: try a fresh ID from the generator.
        }

        throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "SHORT_CODE_GENERATION_FAILED",
                "Failed to generate a unique short code. Please retry.");
    }

    /**
     * Lists all shortened URLs owned by the specified user, ordered newest first.
     *
     * @param ownerId ID of the authenticated user
     * @return list of URLs owned by the user
     */
    @Transactional(readOnly = true)
    public List<Url> listByOwner(Long ownerId) {
        return urlRepository.findAllByUserIdOrderByCreatedAtDesc(ownerId);
    }

    /**
     * Retrieves a single URL by ID ensuring it belongs to the authenticated user.
     *
     * @param id      ID of the URL record
     * @param ownerId ID of the authenticated user
     * @return the {@link Url} record
     * @throws ApiException 404 NOT_FOUND if the URL does not exist or does not belong to the user
     */
    @Transactional(readOnly = true)
    public Url getByIdAndOwner(Long id, Long ownerId) {
        return urlRepository.findByIdAndUserId(id, ownerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "URL_NOT_FOUND",
                        "URL not found."));
    }

    /**
     * Updates the destination URL and invalidates the Redis redirect cache entry.
     *
     * @param id             ID of the URL record
     * @param ownerId        ID of the authenticated user
     * @param newOriginalUrl the new destination URL
     * @return the updated {@link Url} record
     * @throws ApiException 404 NOT_FOUND if the URL does not exist or does not belong to the user
     */
    @Transactional
    public Url updateOriginalUrl(Long id, Long ownerId, String newOriginalUrl) {
        Url url = getByIdAndOwner(id, ownerId);
        if (!url.getOriginalUrl().equals(newOriginalUrl)) {
            url.updateOriginalUrl(newOriginalUrl);
            urlCacheService.evict(url.getShortCode());
            return urlRepository.save(url);
        }
        return url;
    }

    /**
     * Updates the lifecycle status of a URL and invalidates the Redis redirect cache entry.
     *
     * @param id        ID of the URL record
     * @param ownerId   ID of the authenticated user
     * @param newStatus the new lifecycle status
     * @return the updated {@link Url} record
     * @throws ApiException 404 NOT_FOUND if the URL does not exist or does not belong to the user
     */
    @Transactional
    public Url updateStatus(Long id, Long ownerId, UrlStatus newStatus) {
        Url url = getByIdAndOwner(id, ownerId);
        if (url.getStatus() != newStatus) {
            url.updateStatus(newStatus);
            urlCacheService.evict(url.getShortCode());
            return urlRepository.save(url);
        }
        return url;
    }

    /**
     * Deletes a URL and invalidates the Redis redirect cache entry.
     *
     * @param id      ID of the URL record
     * @param ownerId ID of the authenticated user
     * @throws ApiException 404 NOT_FOUND if the URL does not exist or does not belong to the user
     */
    @Transactional
    public void delete(Long id, Long ownerId) {
        Url url = getByIdAndOwner(id, ownerId);
        urlCacheService.evict(url.getShortCode());
        urlRepository.delete(url);
    }

    /**
     * Resolves a short code to its original URL for redirection and emits a click event.
     *
     * <p>Follows the cache-aside pattern:
     * <ol>
     *   <li>Check Redis cache for an existing redirect mapping.</li>
     *   <li>If present (cache HIT), publish click event asynchronously and return immediately.</li>
     *   <li>If absent (cache MISS), query PostgreSQL.</li>
     *   <li>If active, store in Redis with TTL, publish click event asynchronously, and return.</li>
     * </ol>
     *
     * @param shortCode the Base62 short code to resolve
     * @return the original URL destination
     * @throws ApiException 404 NOT_FOUND if the short code does not exist
     * @throws ApiException 410 GONE if the URL is disabled
     */
    @Transactional(readOnly = true)
    public String getRedirectUrl(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "URL_NOT_FOUND",
                    "Short URL not found.");
        }

        // 1. Check Redis cache first (cache-aside)
        Optional<String> cachedUrl = urlCacheService.get(shortCode);
        if (cachedUrl.isPresent()) {
            Optional<UrlCacheService.CachedUrlMetadata> meta = urlCacheService.getMetadata(shortCode);
            if (meta.isPresent()) {
                clickEventProducer.send(new ClickEvent(
                        UUID.randomUUID().toString(),
                        meta.get().urlId(),
                        shortCode,
                        meta.get().userId(),
                        Instant.now()
                ));
            } else {
                urlRepository.findByShortCode(shortCode).ifPresent(u -> {
                    urlCacheService.put(shortCode, u.getOriginalUrl(), u.getId(), u.getUser().getId());
                    clickEventProducer.send(new ClickEvent(
                            UUID.randomUUID().toString(),
                            u.getId(),
                            shortCode,
                            u.getUser().getId(),
                            Instant.now()
                    ));
                });
            }
            return cachedUrl.get();
        }

        // 2. Cache miss -> query authoritative PostgreSQL database
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "URL_NOT_FOUND",
                        "Short URL not found."));

        if (url.getStatus() != UrlStatus.ACTIVE) {
            throw new ApiException(
                    HttpStatus.GONE,
                    "URL_DISABLED",
                    "Short URL is disabled.");
        }

        // 3. Populate Redis cache with the active target URL and metadata
        urlCacheService.put(shortCode, url.getOriginalUrl(), url.getId(), url.getUser().getId());

        // 4. Emit ClickEvent asynchronously to Kafka
        clickEventProducer.send(new ClickEvent(
                UUID.randomUUID().toString(),
                url.getId(),
                shortCode,
                url.getUser().getId(),
                Instant.now()
        ));

        return url.getOriginalUrl();
    }
}
