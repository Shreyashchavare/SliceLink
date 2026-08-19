package com.slicelink.analytics;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for click event persistence and analytics queries.
 */
public interface ClickEventRepository extends JpaRepository<ClickEventEntity, Long> {

    /** Returns {@code true} if an event with the given UUID has already been recorded. */
    boolean existsByEventId(String eventId);

    /** Returns the total count of recorded clicks for a specific URL ID. */
    long countByUrlId(Long urlId);

    /** Finds the 10 most recent click events for a URL, ordered newest first. */
    List<ClickEventEntity> findTop10ByUrlIdOrderByOccurredAtDesc(Long urlId);
}
