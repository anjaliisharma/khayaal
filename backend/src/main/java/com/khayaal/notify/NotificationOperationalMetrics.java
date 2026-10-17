package com.khayaal.notify;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class NotificationOperationalMetrics {
    private final JdbcClient jdbc;
    private final AtomicLong queueDepth = new AtomicLong();
    private final AtomicLong deadCount = new AtomicLong();
    private final AtomicLong missedDoseCount = new AtomicLong();

    public NotificationOperationalMetrics(JdbcClient jdbc, MeterRegistry registry) {
        this.jdbc = jdbc;
        Gauge.builder("khayaal.notifications.queue.depth", queueDepth, AtomicLong::get).register(registry);
        Gauge.builder("khayaal.notifications.dead", deadCount, AtomicLong::get).register(registry);
        Gauge.builder("khayaal.doses.missed", missedDoseCount, AtomicLong::get).register(registry);
    }

    @Scheduled(fixedDelayString = "${app.metrics.refresh-delay-ms:30000}")
    public void refresh() {
        queueDepth.set(count("select count(*) from notifications where status in ('PENDING','FAILED','PROCESSING')"));
        deadCount.set(count("select count(*) from notifications where status='DEAD'"));
        missedDoseCount.set(count("select count(*) from dose_events where status='MISSED'"));
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
