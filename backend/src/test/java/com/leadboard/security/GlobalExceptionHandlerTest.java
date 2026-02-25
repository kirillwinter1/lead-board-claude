package com.leadboard.security;

import com.leadboard.config.GlobalExceptionHandler;
import com.leadboard.team.TeamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("IllegalArgumentException returns 400 with message")
    void illegalArgument_returns400() {
        var response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("bad input", response.getBody().get("error"));
    }

    @Test
    @DisplayName("TeamNotFoundException returns 404 with message")
    void teamNotFound_returns404() {
        var response = handler.handleTeamNotFound(new TeamService.TeamNotFoundException("Team not found"));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Team not found", response.getBody().get("error"));
    }

    @Test
    @DisplayName("Generic exception returns 500 with safe message")
    void genericException_returns500_safeMessage() {
        var response = handler.handleGenericException(
                new RuntimeException("NullPointerException at com.leadboard.internal.Secret:42")
        );
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        // Must NOT contain internal details
        String error = response.getBody().get("error");
        assertFalse(error.contains("NullPointer"));
        assertFalse(error.contains("com.leadboard"));
        assertFalse(error.contains("Secret"));
        assertEquals("An internal error occurred. Please try again later.", error);
    }

    @Test
    @DisplayName("AccessDeniedException returns 403 with generic message")
    void accessDenied_returns403() {
        var response = handler.handleAccessDenied(
                new org.springframework.security.access.AccessDeniedException("forbidden")
        );
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Access denied", response.getBody().get("error"));
        // Must NOT contain original message
        assertFalse(response.getBody().get("error").contains("forbidden"));
    }
}
