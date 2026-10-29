package com.example.itmohighloadsystemshw1.monitoring;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
public class HandlersController {

    private final MonitoredHandlers handlers;

    public HandlersController(MonitoredHandlers handlers) {
        this.handlers = handlers;
    }

    @GetMapping("/handlers")
    public Collection<HandlerId> handlers() {
        return handlers.all();
    }
}
