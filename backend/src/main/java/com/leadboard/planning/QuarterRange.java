package com.leadboard.planning;

import java.time.LocalDate;

/**
 * Represents a fiscal quarter with its date range.
 * Quarter labels follow the format "YYYYQn" (e.g., "2026Q2" = Apr 1 – Jun 30).
 */
public record QuarterRange(String label, LocalDate startDate, LocalDate endDate) {

    /**
     * Parse a quarter label (e.g., "2026Q2") into a QuarterRange with start/end dates.
     */
    public static QuarterRange of(String label) {
        if (label == null || label.length() != 6 || label.charAt(4) != 'Q') {
            throw new IllegalArgumentException("Invalid quarter label: " + label);
        }

        int year;
        int quarter;
        try {
            year = Integer.parseInt(label.substring(0, 4));
            quarter = Integer.parseInt(label.substring(5, 6));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid quarter label: " + label);
        }

        if (quarter < 1 || quarter > 4) {
            throw new IllegalArgumentException("Quarter must be 1-4, got: " + quarter);
        }

        int startMonth = (quarter - 1) * 3 + 1;
        LocalDate start = LocalDate.of(year, startMonth, 1);
        LocalDate end = start.plusMonths(3).minusDays(1);

        return new QuarterRange(label, start, end);
    }

    /**
     * Determine the quarter label for a given date.
     */
    public static String labelForDate(LocalDate date) {
        int quarter = (date.getMonthValue() - 1) / 3 + 1;
        return date.getYear() + "Q" + quarter;
    }

    /**
     * Get the current quarter label.
     */
    public static String currentQuarterLabel() {
        return labelForDate(LocalDate.now());
    }
}
