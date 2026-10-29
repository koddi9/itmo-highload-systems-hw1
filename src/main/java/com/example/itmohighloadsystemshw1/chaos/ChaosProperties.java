package com.example.itmohighloadsystemshw1.chaos;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Искусственные задержки и 500 для ручек /items.
 *
 * @param failureRate доля ответов 500, от 0 до 1
 * @param latencyMean средняя дополнительная задержка, 0 - выключена
 */
@ConfigurationProperties("chaos")
public record ChaosProperties(double failureRate, Duration latencyMean) {

    public ChaosProperties {
        if (failureRate < 0 || failureRate > 1) {
            throw new IllegalArgumentException("chaos.failure-rate must be within [0, 1], got " + failureRate);
        }
        if (latencyMean == null || latencyMean.isNegative()) {
            latencyMean = Duration.ZERO;
        }
    }
}
