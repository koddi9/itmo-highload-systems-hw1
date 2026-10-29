package com.example.itmohighloadsystemshw1.chaos;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR, reason = "Injected failure")
public class InjectedFailureException extends RuntimeException {

    public InjectedFailureException() {
        super("Injected failure", null, false, false);
    }
}
