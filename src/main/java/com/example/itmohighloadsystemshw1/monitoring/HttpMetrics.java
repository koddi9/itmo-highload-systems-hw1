package com.example.itmohighloadsystemshw1.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Latency - гистограмма: бакеты можно суммировать по подам перед histogram_quantile.
 * Код ответа есть только у счётчика, чтобы не умножать серии бакетов на число кодов.
 * Лейблы pod и datacenter задаются в application.properties.
 */
@Component
public class HttpMetrics {

    static final String LATENCY = "app.http.request.duration";
    static final String REQUESTS = "app.http.requests";

    private static final Duration[] BUCKETS = {
            Duration.ofMillis(1), Duration.ofMillis(2).plusNanos(500_000), Duration.ofMillis(5),
            Duration.ofMillis(10), Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(75),
            Duration.ofMillis(100), Duration.ofMillis(150), Duration.ofMillis(250), Duration.ofMillis(500),
            Duration.ofMillis(750), Duration.ofSeconds(1), Duration.ofMillis(2500), Duration.ofSeconds(5),
    };

    private final MeterProvider<Timer> latency;
    private final MeterProvider<Counter> requests;

    public HttpMetrics(MeterRegistry registry) {
        this.latency = Timer.builder(LATENCY)
                .description("Time from request entry to response completion of a monitored handler")
                .serviceLevelObjectives(BUCKETS)
                .withRegistry(registry);
        this.requests = Counter.builder(REQUESTS)
                .description("Completed requests of a monitored handler by response status code")
                .withRegistry(registry);
    }

    // Серии создаются заранее, чтобы ручка была видна до первого запроса.
    void register(HandlerId handler) {
        latency.withTags(tags(handler));
    }

    void record(HandlerId handler, int status, long durationNanos) {
        latency.withTags(tags(handler)).record(durationNanos, TimeUnit.NANOSECONDS);
        requests.withTags(tags(handler).and("status", Integer.toString(status))).increment();
    }

    private static Tags tags(HandlerId handler) {
        return Tags.of("method", handler.method(), "route", handler.route());
    }
}
