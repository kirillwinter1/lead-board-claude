package com.leadboard.quality.fix;

/**
 * Thrown when a fix is requested but the underlying violation is no longer present on fresh
 * DB state (e.g. a concurrent edit or sync already resolved it). Mapped to HTTP 409 Conflict.
 */
public class FixConflictException extends RuntimeException {
    public FixConflictException(String message) {
        super(message);
    }
}
