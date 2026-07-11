package com.leadboard.poker;

/**
 * A poker state-machine violation (e.g. reveal on a non-VOTING story, edit a story
 * that is no longer PENDING). Mapped to HTTP 409 CONFLICT by GlobalExceptionHandler.
 *
 * <p>Deliberately a dedicated type rather than a raw {@link IllegalStateException}:
 * the 409 mapping (which echoes the message to the client) must apply ONLY to poker
 * flow guards, not to every {@code IllegalStateException} in the app (config/simulation
 * guards etc.), which would leak internal messages and change unrelated status codes.
 */
public class PokerStateException extends IllegalStateException {
    public PokerStateException(String message) {
        super(message);
    }
}
