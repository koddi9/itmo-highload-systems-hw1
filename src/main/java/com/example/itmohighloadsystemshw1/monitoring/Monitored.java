package com.example.itmohighloadsystemshw1.monitoring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Контроллер или метод, для ручек которого собираются latency, traffic и errors.
 * Эти же ручки отдаёт GET /handlers. Служебные /health и /metrics не помечены.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Monitored {
}
