package com.slicelink.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClickEventTest {

    @Test
    @DisplayName("ClickEvent records values correctly and maintains immutability")
    void clickEvent_storesFieldsCorrectly() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        ClickEvent event = new ClickEvent(eventId, 12345L, "3D7gK", 1L, now);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.urlId()).isEqualTo(12345L);
        assertThat(event.shortCode()).isEqualTo("3D7gK");
        assertThat(event.userId()).isEqualTo(1L);
        assertThat(event.occurredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("ClickEvents with same values are equal")
    void clickEvent_equalityAndHashCode() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        ClickEvent event1 = new ClickEvent(eventId, 12345L, "3D7gK", 1L, now);
        ClickEvent event2 = new ClickEvent(eventId, 12345L, "3D7gK", 1L, now);

        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }
}
