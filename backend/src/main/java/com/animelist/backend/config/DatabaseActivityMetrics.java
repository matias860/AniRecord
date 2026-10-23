package com.animelist.backend.config;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Records application-side database activity without querying PostgreSQL. The
 * estimated active time merges acquisitions separated by less than Neon's
 * five-minute scale-to-zero window.
 */
@Component
public class DatabaseActivityMetrics implements MetricsTrackerFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseActivityMetrics.class);
    private static final Duration SCALE_TO_ZERO_WINDOW = Duration.ofMinutes(5);
    private final LongAdder connectionsCreated = new LongAdder();
    private final LongAdder connectionsAcquired = new LongAdder();
    private final LongAdder connectionTimeouts = new LongAdder();
    private final LongAdder usageMillis = new LongAdder();
    private final Clock clock;
    private volatile PoolStats poolStats;
    private Instant windowStartedAt;
    private Instant lastActivityAt;
    private long completedActiveMillis;

    public DatabaseActivityMetrics() {
        this(Clock.systemUTC());
    }

    DatabaseActivityMetrics(Clock clock) {
        this.clock = clock;
    }

    @Override
    public IMetricsTracker create(String poolName, PoolStats stats) {
        poolStats = stats;
        return new IMetricsTracker() {
            @Override public void recordConnectionCreatedMillis(long elapsedMillis) {
                connectionsCreated.increment();
            }
            @Override public void recordConnectionAcquiredNanos(long elapsedNanos) {
                connectionsAcquired.increment();
                recordActivity(clock.instant());
            }
            @Override public void recordConnectionUsageMillis(long elapsedMillis) {
                usageMillis.add(elapsedMillis);
            }
            @Override public void recordConnectionTimeout() {
                connectionTimeouts.increment();
            }
        };
    }

    @Scheduled(initialDelayString = "${app.database-metrics.initial-delay:PT6H}",
            fixedDelayString = "${app.database-metrics.report-delay:PT6H}")
    public void report() {
        Snapshot snapshot = snapshot(clock.instant());
        LOGGER.info("Database activity since startup: physicalConnections={}, acquisitions={}, usageSeconds={}, "
                        + "estimatedNeonActiveMinutes={}, timeouts={}, poolTotal={}, poolActive={}, poolIdle={}",
                snapshot.connectionsCreated(), snapshot.connectionsAcquired(), snapshot.usageMillis() / 1_000,
                snapshot.estimatedActiveMillis() / 60_000, snapshot.connectionTimeouts(), snapshot.poolTotal(),
                snapshot.poolActive(), snapshot.poolIdle());
    }

    private synchronized void recordActivity(Instant now) {
        if (lastActivityAt == null || now.isAfter(lastActivityAt.plus(SCALE_TO_ZERO_WINDOW))) {
            closeCurrentWindow();
            windowStartedAt = now;
        }
        lastActivityAt = now;
    }

    synchronized Snapshot snapshot(Instant now) {
        long activeMillis = completedActiveMillis;
        if (windowStartedAt != null) {
            Instant estimatedEnd = lastActivityAt.plus(SCALE_TO_ZERO_WINDOW);
            Instant boundedEnd = estimatedEnd.isBefore(now) ? estimatedEnd : now;
            activeMillis += Math.max(0, Duration.between(windowStartedAt, boundedEnd).toMillis());
        }
        PoolStats stats = poolStats;
        return new Snapshot(connectionsCreated.sum(), connectionsAcquired.sum(), connectionTimeouts.sum(),
                usageMillis.sum(), activeMillis, stats == null ? 0 : stats.getTotalConnections(),
                stats == null ? 0 : stats.getActiveConnections(), stats == null ? 0 : stats.getIdleConnections());
    }

    private void closeCurrentWindow() {
        if (windowStartedAt == null) return;
        completedActiveMillis += Duration.between(windowStartedAt,
                lastActivityAt.plus(SCALE_TO_ZERO_WINDOW)).toMillis();
    }

    record Snapshot(long connectionsCreated, long connectionsAcquired, long connectionTimeouts,
            long usageMillis, long estimatedActiveMillis, int poolTotal, int poolActive, int poolIdle) {}
}
