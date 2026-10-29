package com.example.itmohighloadsystemshw1.chaos;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class FaultInjector {

    private static final long MAX_DELAY_NANOS = Duration.ofSeconds(1).toNanos();

    private final ChaosProperties properties;

    public FaultInjector(ChaosProperties properties) {
        this.properties = properties;
    }

    // Экспоненциальная задержка даёт длинный хвост, поэтому p99 заметно больше p50.
    public void inject() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long meanNanos = properties.latencyMean().toNanos();
        if (meanNanos > 0) {
            long delayNanos = (long) (-Math.log(1 - random.nextDouble()) * meanNanos);
            sleep(Duration.ofNanos(Math.min(delayNanos, MAX_DELAY_NANOS)));
        }
        if (random.nextDouble() < properties.failureRate()) {
            throw new InjectedFailureException();
        }
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
