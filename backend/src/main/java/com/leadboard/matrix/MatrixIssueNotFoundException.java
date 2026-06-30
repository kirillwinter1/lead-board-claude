package com.leadboard.matrix;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a triage target cannot be found or is not a valid orphan task
 * (must exist, have no parent, and be board_category STORY). Mapped to HTTP 404
 * so the frontend can distinguish "no such triageable task" from a 400 validation
 * error (invalid quadrant value).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MatrixIssueNotFoundException extends RuntimeException {
    public MatrixIssueNotFoundException(String message) {
        super(message);
    }
}
