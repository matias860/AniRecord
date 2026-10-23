package com.animelist.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.PoolStats;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DatabaseActivityMetricsTest {
    @Test
    void mergesActivityInsideScaleToZeroWindowAndSeparatesLaterActivity() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));
        DatabaseActivityMetrics metrics = new DatabaseActivityMetrics(clock);
        IMetricsTracker tracker = metrics.create("test", new EmptyPoolStats());

        tracker.recordConnectionCreatedMillis(20);
        tracker.recordConnectionAcquiredNanos(1_000);
        tracker.recordConnectionUsageMillis(30);
        clock.advanceSeconds(180);
        tracker.recordConnectionAcquiredNanos(1_000);
        clock.advanceSeconds(420);
        tracker.recordConnectionAcquiredNanos(1_000);
        clock.advanceSeconds(120);

        DatabaseActivityMetrics.Snapshot snapshot = metrics.snapshot(clock.instant());
        assertEquals(1, snapshot.connectionsCreated());
        assertEquals(3, snapshot.connectionsAcquired());
        assertEquals(30, snapshot.usageMillis());
        assertEquals(10 * 60_000, snapshot.estimatedActiveMillis());
    }

    private static final class EmptyPoolStats extends PoolStats {
        private EmptyPoolStats() { super(0); }
        @Override protected void update() {}
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
