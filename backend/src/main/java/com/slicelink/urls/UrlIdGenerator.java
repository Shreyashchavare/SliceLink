package com.slicelink.urls;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Generates time-ordered, collision-safe numeric identifiers for URL records
 * in a single-node deployment.
 *
 * <h2>Structure (64-bit signed long)</h2>
 * <pre>
 *  Bit 63      : sign bit — always 0 (keeps IDs positive)
 *  Bits 62–22  : 41 bits of milliseconds since custom epoch
 *                → overflows in ~69 years (epoch: 2024-01-01T00:00:00Z)
 *  Bits 21–10  : 12 bits of node ID (0–4095); hard-coded to 0 for monolith,
 *                set via constructor for future distributed deployment
 *  Bits  9– 0  : 10 bits of per-millisecond sequence (0–1023)
 * </pre>
 *
 * <p>This layout is compatible with Snowflake-style IDs and can be upgraded to
 * a distributed generator by injecting a non-zero node ID without changing the
 * public API or the database schema.
 *
 * <p>Maximum throughput: 1 024 unique IDs per millisecond per node.
 * If the sequence overflows within a millisecond the generator busy-waits
 * until the clock advances rather than wrapping and risking a collision.
 *
 * <p>Thread-safe.
 */
@Component
public class UrlIdGenerator {

    /** 2024-01-01T00:00:00Z in epoch-milliseconds. */
    private static final long EPOCH_MS = 1_704_067_200_000L;

    private static final int NODE_ID_BITS    = 12;
    private static final int SEQUENCE_BITS   = 10;
    private static final int MAX_NODE_ID     = (1 << NODE_ID_BITS) - 1;   // 4095
    private static final int MAX_SEQUENCE    = (1 << SEQUENCE_BITS) - 1;  // 1023

    private static final int TIMESTAMP_SHIFT = NODE_ID_BITS + SEQUENCE_BITS; // 22
    private static final int NODE_SHIFT      = SEQUENCE_BITS;                // 10

    private final long nodeIdBits;

    private long lastTimestamp = -1L;
    private final AtomicInteger sequence = new AtomicInteger(0);

    /** Single-node production constructor (nodeId = 0). */
    public UrlIdGenerator() {
        this(0);
    }

    /**
     * Constructor for multi-node deployment.
     *
     * @param nodeId worker/machine ID in range [0, 4095]
     */
    public UrlIdGenerator(int nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException(
                    "nodeId must be in [0, " + MAX_NODE_ID + "], got: " + nodeId);
        }
        this.nodeIdBits = (long) nodeId << NODE_SHIFT;
    }

    /**
     * Generates the next unique identifier.
     *
     * @return a positive, time-ordered {@code long} ID
     */
    public synchronized long nextId() {
        long now = currentMillis();

        if (now < lastTimestamp) {
            long offset = lastTimestamp - now;
            throw new IllegalStateException(
                    "Clock moved backwards. Refusing to generate id for " + offset + " milliseconds.");
        }

        if (now == lastTimestamp) {
            int seq = sequence.incrementAndGet();
            if (seq > MAX_SEQUENCE) {
                // Sequence exhausted — wait for the next millisecond.
                now = waitForNextMillis(lastTimestamp);
                sequence.set(0);
            }
        } else {
            sequence.set(0);
        }

        lastTimestamp = now;

        long elapsed = now - EPOCH_MS;
        return (elapsed << TIMESTAMP_SHIFT) | nodeIdBits | sequence.get();
    }

    // visible for testing
    long currentMillis() {
        return Instant.now().toEpochMilli();
    }

    private long waitForNextMillis(long last) {
        long now;
        do {
            now = currentMillis();
        } while (now <= last);
        return now;
    }
}
