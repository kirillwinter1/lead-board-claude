package com.leadboard.jira;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Wraps non-2xx responses from the Jira REST API with enough context
 * (issue key + Jira response body) for callers to surface a meaningful
 * error message instead of a bare 500.
 *
 * <p>Mapped to HTTP 502 BAD_GATEWAY by default — the upstream Jira call
 * failed, our service itself is healthy.</p>
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class JiraClientException extends RuntimeException {
    public JiraClientException(String message) {
        super(message);
    }

    public JiraClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
