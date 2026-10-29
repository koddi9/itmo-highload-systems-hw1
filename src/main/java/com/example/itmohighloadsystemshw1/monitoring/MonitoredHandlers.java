package com.example.itmohighloadsystemshw1.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Ручки контроллеров с {@link Monitored}. Этот же список используют фильтр и GET /handlers,
 * поэтому дашборд совпадает с тем, что реально измеряется.
 */
@Component
public class MonitoredHandlers implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MonitoredHandlers.class);

    private final ObjectProvider<RequestMappingHandlerMapping> mappings;
    private final HttpMetrics metrics;
    private volatile Set<HandlerId> handlers = Set.of();

    public MonitoredHandlers(ObjectProvider<RequestMappingHandlerMapping> mappings, HttpMetrics metrics) {
        this.mappings = mappings;
        this.metrics = metrics;
    }

    @Override
    public void afterSingletonsInstantiated() {
        SortedSet<HandlerId> discovered = new TreeSet<>(HandlerId.ORDER);
        mappings.orderedStream().forEach(mapping -> mapping.getHandlerMethods().forEach((info, method) -> {
            if (isMonitored(method)) {
                addHandlers(discovered, info, method);
            }
        }));
        discovered.forEach(metrics::register);
        handlers = Collections.unmodifiableSortedSet(discovered);
        log.info("HTTP metrics are collected for {}", handlers);
    }

    public Set<HandlerId> all() {
        return handlers;
    }

    public boolean contains(HandlerId handler) {
        return handlers.contains(handler);
    }

    private static boolean isMonitored(HandlerMethod method) {
        return method.hasMethodAnnotation(Monitored.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), Monitored.class);
    }

    private static void addHandlers(Set<HandlerId> target, RequestMappingInfo info, HandlerMethod method) {
        Set<RequestMethod> httpMethods = info.getMethodsCondition().getMethods();
        if (httpMethods.isEmpty()) {
            log.warn("{} accepts any HTTP method and is not monitored: declare the method explicitly", method);
            return;
        }
        for (String route : info.getPatternValues()) {
            for (RequestMethod httpMethod : httpMethods) {
                target.add(new HandlerId(httpMethod.name(), route));
            }
        }
    }
}
