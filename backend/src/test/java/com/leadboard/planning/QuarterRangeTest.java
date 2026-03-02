package com.leadboard.planning;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class QuarterRangeTest {

    @Test
    void testParseQ1() {
        QuarterRange range = QuarterRange.of("2026Q1");
        assertEquals("2026Q1", range.label());
        assertEquals(LocalDate.of(2026, 1, 1), range.startDate());
        assertEquals(LocalDate.of(2026, 3, 31), range.endDate());
    }

    @Test
    void testParseQ2() {
        QuarterRange range = QuarterRange.of("2026Q2");
        assertEquals("2026Q2", range.label());
        assertEquals(LocalDate.of(2026, 4, 1), range.startDate());
        assertEquals(LocalDate.of(2026, 6, 30), range.endDate());
    }

    @Test
    void testParseQ3() {
        QuarterRange range = QuarterRange.of("2026Q3");
        assertEquals(LocalDate.of(2026, 7, 1), range.startDate());
        assertEquals(LocalDate.of(2026, 9, 30), range.endDate());
    }

    @Test
    void testParseQ4() {
        QuarterRange range = QuarterRange.of("2026Q4");
        assertEquals(LocalDate.of(2026, 10, 1), range.startDate());
        assertEquals(LocalDate.of(2026, 12, 31), range.endDate());
    }

    @Test
    void testInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> QuarterRange.of("invalid"));
        assertThrows(IllegalArgumentException.class, () -> QuarterRange.of("2026Q5"));
        assertThrows(IllegalArgumentException.class, () -> QuarterRange.of("2026Q0"));
        assertThrows(IllegalArgumentException.class, () -> QuarterRange.of(null));
    }

    @Test
    void testLabelForDate() {
        assertEquals("2026Q1", QuarterRange.labelForDate(LocalDate.of(2026, 1, 15)));
        assertEquals("2026Q1", QuarterRange.labelForDate(LocalDate.of(2026, 3, 31)));
        assertEquals("2026Q2", QuarterRange.labelForDate(LocalDate.of(2026, 4, 1)));
        assertEquals("2026Q2", QuarterRange.labelForDate(LocalDate.of(2026, 6, 30)));
        assertEquals("2026Q3", QuarterRange.labelForDate(LocalDate.of(2026, 7, 1)));
        assertEquals("2026Q4", QuarterRange.labelForDate(LocalDate.of(2026, 10, 1)));
        assertEquals("2026Q4", QuarterRange.labelForDate(LocalDate.of(2026, 12, 31)));
    }

    @Test
    void testCurrentQuarterLabel() {
        String label = QuarterRange.currentQuarterLabel();
        assertNotNull(label);
        assertTrue(label.matches("\\d{4}Q[1-4]"));
    }
}
