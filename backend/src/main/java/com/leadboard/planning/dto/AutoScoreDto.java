package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * DTO для AutoScore эпика.
 */
public record AutoScoreDto(
        String epicKey,
        String summary,
        BigDecimal autoScore,
        OffsetDateTime calculatedAt,
        Map<String, BigDecimal> factors
) {
    /**
     * Создаёт DTO с базовой информацией (без детализации факторов).
     */
    public static AutoScoreDto basic(String epicKey, String summary, BigDecimal autoScore,
                                     OffsetDateTime calculatedAt) {
        return new AutoScoreDto(epicKey, summary, autoScore, calculatedAt, null);
    }
}
