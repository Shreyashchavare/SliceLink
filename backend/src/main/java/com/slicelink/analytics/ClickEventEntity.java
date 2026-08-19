package com.slicelink.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Persistent click event entity for PostgreSQL analytics storage.
 */
@Entity
@Table(name = "click_events")
public class ClickEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36, updatable = false)
    private String eventId;

    @Column(name = "url_id", nullable = false, updatable = false)
    private Long urlId;

    @Column(name = "short_code", nullable = false, length = 12, updatable = false)
    private String shortCode;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ClickEventEntity() { }

    public ClickEventEntity(String eventId, Long urlId, String shortCode, Long userId, Instant occurredAt) {
        this.eventId    = eventId;
        this.urlId      = urlId;
        this.shortCode  = shortCode;
        this.userId     = userId;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public Long getUrlId() { return urlId; }
    public String getShortCode() { return shortCode; }
    public Long getUserId() { return userId; }
    public Instant getOccurredAt() { return occurredAt; }
}
