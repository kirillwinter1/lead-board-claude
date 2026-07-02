package com.leadboard.quality.fix.dto;

import java.util.Map;

/**
 * Body of POST /api/data-quality/fix.
 *
 * @param issueKey the issue with the violation
 * @param rule     the {@code DataQualityRule} name
 * @param choiceId selected choice id (for multi-choice fixes; null otherwise)
 * @param params   user-supplied input values keyed by {@link FixInput#name()}
 */
public record FixRequest(
        String issueKey,
        String rule,
        String choiceId,
        Map<String, Object> params
) {
    public Map<String, Object> paramsOrEmpty() {
        return params != null ? params : Map.of();
    }
}
