package com.slicelink.analytics;

import com.slicelink.shared.ApiException;
import com.slicelink.urls.Url;
import com.slicelink.urls.UrlRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for click analytics recording and retrieval.
 */
@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final ClickEventRepository clickEventRepository;
    private final UrlRepository urlRepository;

    public AnalyticsService(ClickEventRepository clickEventRepository, UrlRepository urlRepository) {
        this.clickEventRepository = clickEventRepository;
        this.urlRepository = urlRepository;
    }

    /**
     * Idempotently persists a click event to PostgreSQL.
     *
     * @param event the consumed click event
     */
    @Transactional
    public void recordClick(ClickEvent event) {
        if (event == null || event.eventId() == null) {
            return;
        }

        if (clickEventRepository.existsByEventId(event.eventId())) {
            log.debug("Duplicate click event {} ignored idempotently", event.eventId());
            return;
        }

        try {
            ClickEventEntity entity = new ClickEventEntity(
                    event.eventId(),
                    event.urlId(),
                    event.shortCode(),
                    event.userId(),
                    event.occurredAt()
            );
            clickEventRepository.save(entity);
            log.debug("Recorded click event {} for urlId {}", event.eventId(), event.urlId());
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate click event {} caught via unique constraint, safely ignored", event.eventId());
        }
    }

    /**
     * Retrieves aggregated click analytics for a specific URL ensuring ownership.
     *
     * @param urlId   ID of the URL record
     * @param ownerId ID of the authenticated user requesting analytics
     * @return {@link UrlAnalyticsResponse} containing total clicks and recent timestamps
     * @throws ApiException 404 NOT_FOUND if the URL does not exist or does not belong to the user
     */
    @Transactional(readOnly = true)
    public UrlAnalyticsResponse getAnalytics(Long urlId, Long ownerId) {
        Url url = urlRepository.findByIdAndUserId(urlId, ownerId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "URL_NOT_FOUND",
                        "URL not found."));

        long totalClicks = clickEventRepository.countByUrlId(urlId);
        List<Instant> recentClicks = clickEventRepository.findTop10ByUrlIdOrderByOccurredAtDesc(urlId)
                .stream()
                .map(ClickEventEntity::getOccurredAt)
                .toList();

        return new UrlAnalyticsResponse(url.getId(), url.getShortCode(), totalClicks, recentClicks);
    }
}
