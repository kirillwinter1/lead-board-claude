package com.leadboard.planning;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested project cannot be located or when the referenced
 * issue is not a project (board category != PROJECT). Mapped to HTTP 404
 * so the frontend can distinguish "not found" from validation errors (400).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(String message) {
        super(message);
    }
}
