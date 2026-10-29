package com.example.itmohighloadsystemshw1.monitoring;

import java.util.Comparator;

/** HTTP-метод и шаблон пути (/items/{id}, а не /items/42). */
public record HandlerId(String method, String route) {

    static final Comparator<HandlerId> ORDER =
            Comparator.comparing(HandlerId::route).thenComparing(HandlerId::method);

    @Override
    public String toString() {
        return method + " " + route;
    }
}
