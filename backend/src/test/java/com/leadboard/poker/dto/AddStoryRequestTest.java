package com.leadboard.poker.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SECURITY_AUDIT.md #4 (HIGH) — JQL injection via existingStoryKey in Planning Poker.
 * existingStoryKey flows unmodified into JiraClient.getSubtasks(), which used to build
 * JQL by raw string concatenation. These tests lock in the DTO-level guard rail: only
 * well-formed Jira issue keys (or no key at all) may pass validation.
 */
class AddStoryRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("rejects a classic JQL injection payload in existingStoryKey")
    void rejectsJqlInjectionPayload() {
        AddStoryRequest request = new AddStoryRequest(
                "Some title", List.of("DEV"), "X OR project = SECRET");

        Set<ConstraintViolation<AddStoryRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "expected a validation error for the injected key");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("existingStoryKey")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "X OR project = SECRET",
            "ABC-123 OR 1=1",
            "'; DROP TABLE poker_story;--",
            "ABC-123\" OR \"1\"=\"1",
            "abc-123",      // lowercase project key not allowed
            "ABC123",       // missing dash
            "-123",         // missing project key
            "ABC-",         // missing issue number
            "ABC-12A",      // trailing non-digit
            " ABC-123",     // leading whitespace
            "ABC-123 ",     // trailing whitespace
            "",             // blank — optional field must be omitted (null), not blank
    })
    @DisplayName("rejects malformed / injected existingStoryKey values")
    void rejectsMalformedKeys(String malicious) {
        AddStoryRequest request = new AddStoryRequest("Some title", List.of("DEV"), malicious);

        Set<ConstraintViolation<AddStoryRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "expected '" + malicious + "' to be rejected");
    }

    @Test
    @DisplayName("allows a null existingStoryKey (creating a brand-new Jira story)")
    void allowsNullStoryKey() {
        AddStoryRequest request = new AddStoryRequest("Some title", List.of("DEV"), null);

        Set<ConstraintViolation<AddStoryRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty(), "null existingStoryKey must remain valid (optional field)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC-123", "LEAD-1", "AB2C-9999"})
    @DisplayName("allows well-formed Jira issue keys")
    void allowsValidStoryKeys(String validKey) {
        AddStoryRequest request = new AddStoryRequest("Some title", List.of("DEV"), validKey);

        Set<ConstraintViolation<AddStoryRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty(), "expected '" + validKey + "' to be accepted");
    }
}
