package com.leadboard.planning;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested epic cannot be located.
 * Mapped to HTTP 404 so the frontend can distinguish "not found"
 * from validation errors (which return 400).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EpicNotFoundException extends RuntimeException {
    public EpicNotFoundException(String message) {
        super(message);
    }
}
