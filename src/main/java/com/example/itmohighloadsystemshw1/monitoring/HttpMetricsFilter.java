package com.example.itmohighloadsystemshw1.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

/**
 * Оборачивает весь dispatch, поэтому статус читается уже после exception resolver'ов (404, 400).
 * В route пишется шаблон пути, а не фактический путь, чтобы число серий не росло с числом id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HttpMetricsFilter extends OncePerRequestFilter {

    private final MonitoredHandlers handlers;
    private final HttpMetrics metrics;

    public HttpMetricsFilter(MonitoredHandlers handlers, HttpMetrics metrics) {
        this.handlers = handlers;
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            HandlerId handler = matchedHandler(request);
            if (handler != null && handlers.contains(handler)) {
                // Вылетевшее исключение контейнер отдаст как 500, хотя в response пока может стоять 200.
                int status = failed && response.getStatus() < 400 ? 500 : response.getStatus();
                metrics.record(handler, status, System.nanoTime() - start);
            }
        }
    }

    private static HandlerId matchedHandler(HttpServletRequest request) {
        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return route == null ? null : new HandlerId(request.getMethod(), route.toString());
    }
}
