package com.leadboard.quality;

import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * F84 regression: a Data Quality request without a resolvable tenant context (no subdomain/slug)
 * queries the tenant-scoped {@code jira_issues} table on the {@code public} search_path, where it
 * does not exist. Hibernate surfaces this as {@link InvalidDataAccessResourceUsageException}
 * ("relation ... does not exist"). The controller must degrade to a clean 400 with a sanitized
 * message — never a 500 leaking SQL.
 */
class DataQualityControllerExceptionTest {

    @Test
    void missingTenantSchema_mapsToCleanBadRequest() {
        DataQualityController controller =
                new DataQualityController(null, null, null, null, null, null);

        InvalidDataAccessResourceUsageException ex = new InvalidDataAccessResourceUsageException(
                "JDBC exception executing SQL [select ... from jira_issues ...]",
                new SQLException("ERROR: relation \"jira_issues\" does not exist"));

        ResponseEntity<Map<String, Object>> response = controller.handleNoTenantSchema(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(false, response.getBody().get("success"));
        String message = String.valueOf(response.getBody().get("message"));
        // Sanitized: no SQL / table names / stack trace leaked to the client.
        assertFalse(message.toLowerCase().contains("jira_issues"));
        assertFalse(message.toLowerCase().contains("select"));
    }
}
