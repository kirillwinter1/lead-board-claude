package com.leadboard.planning.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * F86: Remaining work for a single epic, as computed by the auto-planner.
 *
 * <p>Two views are provided per epic:</p>
 * <ul>
 *   <li>{@code remainingNow*} — unfinished work as of today (person-days per role).</li>
 *   <li>{@code remainingAtQuarterStart*} — the portion of that work the auto-planner
 *       still schedules on/after the quarter's start date (i.e. what it does not manage
 *       to burn down between today and the quarter start). This is the amount that
 *       actually needs to be planned into the new quarter.</li>
 * </ul>
 *
 * <p>Role codes are dynamic (keys of the auto-planner's phase maps) — never hardcoded.</p>
 */
public record EpicRemainingDto(
        String epicKey,
        Map<String, BigDecimal> remainingNowByRole,
        Map<String, BigDecimal> remainingAtQuarterStartByRole,
        BigDecimal remainingNowDays,
        BigDecimal remainingAtQuarterStartDays,
        boolean hasEstimate
) {}
