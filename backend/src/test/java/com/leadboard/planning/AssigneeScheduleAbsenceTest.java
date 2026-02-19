package com.leadboard.planning;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AssigneeScheduleAbsenceTest {

    private final AssigneeSchedule.WorkCalendarHelper calendarHelper = new AssigneeSchedule.WorkCalendarHelper() {
        @Override
        public LocalDate ensureWorkday(LocalDate date) {
            while (date.getDayOfWeek().getValue() > 5) {
                date = date.plusDays(1);
            }
            return date;
        }

        @Override
        public LocalDate nextWorkday(LocalDate date) {
            date = date.plusDays(1);
            return ensureWorkday(date);
        }

        @Override
        public boolean isWorkday(LocalDate date) {
            return date.getDayOfWeek().getValue() <= 5;
        }
    };

    @Test
    void blockAbsenceDatesReducesAvailableHoursToZero() {
        AssigneeSchedule schedule = new AssigneeSchedule("user1", "User 1", "DEV", new BigDecimal("6"));

        LocalDate monday = LocalDate.of(2026, 3, 2); // Monday
        assertEquals(new BigDecimal("6"), schedule.getAvailableHours(monday));

        schedule.blockAbsenceDates(Set.of(monday));
        assertEquals(BigDecimal.ZERO, schedule.getAvailableHours(monday).stripTrailingZeros());
    }

    @Test
    void blockAbsenceDatesHandlesMultipleDates() {
        AssigneeSchedule schedule = new AssigneeSchedule("user1", "User 1", "DEV", new BigDecimal("8"));

        LocalDate day1 = LocalDate.of(2026, 3, 2);
        LocalDate day2 = LocalDate.of(2026, 3, 3);
        LocalDate day3 = LocalDate.of(2026, 3, 4);

        schedule.blockAbsenceDates(Set.of(day1, day2, day3));

        assertEquals(BigDecimal.ZERO, schedule.getAvailableHours(day1).stripTrailingZeros());
        assertEquals(BigDecimal.ZERO, schedule.getAvailableHours(day2).stripTrailingZeros());
        assertEquals(BigDecimal.ZERO, schedule.getAvailableHours(day3).stripTrailingZeros());

        // Day 4 should still be available
        LocalDate day4 = LocalDate.of(2026, 3, 5);
        assertEquals(new BigDecimal("8"), schedule.getAvailableHours(day4));
    }

    @Test
    void blockAbsenceDatesHandlesNullSet() {
        AssigneeSchedule schedule = new AssigneeSchedule("user1", "User 1", "DEV", new BigDecimal("6"));
        schedule.blockAbsenceDates(null);

        // No exception, hours unchanged
        assertEquals(new BigDecimal("6"), schedule.getAvailableHours(LocalDate.of(2026, 3, 2)));
    }

    @Test
    void blockAbsenceDatesHandlesEmptySet() {
        AssigneeSchedule schedule = new AssigneeSchedule("user1", "User 1", "DEV", new BigDecimal("6"));
        schedule.blockAbsenceDates(Set.of());

        assertEquals(new BigDecimal("6"), schedule.getAvailableHours(LocalDate.of(2026, 3, 2)));
    }

    @Test
    void allocateHoursSkipsBlockedDates() {
        AssigneeSchedule schedule = new AssigneeSchedule("user1", "User 1", "DEV", new BigDecimal("8"));

        // Block Tuesday March 3
        LocalDate tuesday = LocalDate.of(2026, 3, 3);
        schedule.blockAbsenceDates(Set.of(tuesday));

        // Allocate 16h starting Monday March 2 (should take Monday + skip Tuesday + use Wednesday)
        LocalDate monday = LocalDate.of(2026, 3, 2);
        AssigneeSchedule.AllocationResult result = schedule.allocateHours(
                new BigDecimal("16"), monday, calendarHelper);

        assertEquals(monday, result.startDate());
        // End date should be Wednesday (March 4), not Tuesday
        assertEquals(LocalDate.of(2026, 3, 4), result.endDate());
        assertEquals(new BigDecimal("16"), result.hoursAllocated());
    }

    @Test
    void allocateHoursSkipsMultipleBlockedDays() {
        AssigneeSchedule schedule = new AssigneeSchedule("user1", "User 1", "DEV", new BigDecimal("8"));

        // Block entire week except Friday
        LocalDate mon = LocalDate.of(2026, 3, 2);
        LocalDate tue = LocalDate.of(2026, 3, 3);
        LocalDate wed = LocalDate.of(2026, 3, 4);
        LocalDate thu = LocalDate.of(2026, 3, 5);
        schedule.blockAbsenceDates(Set.of(mon, tue, wed, thu));

        // Allocate 8h starting Monday - should land on Friday
        AssigneeSchedule.AllocationResult result = schedule.allocateHours(
                new BigDecimal("8"), mon, calendarHelper);

        LocalDate friday = LocalDate.of(2026, 3, 6);
        assertEquals(friday, result.startDate());
        assertEquals(friday, result.endDate());
    }

    @Test
    void findFirstAvailableDateSkipsBlockedDates() {
        AssigneeSchedule schedule = new AssigneeSchedule("user1", "User 1", "DEV", new BigDecimal("8"));

        LocalDate monday = LocalDate.of(2026, 3, 2);
        LocalDate tuesday = LocalDate.of(2026, 3, 3);
        schedule.blockAbsenceDates(Set.of(monday, tuesday));

        LocalDate firstAvailable = schedule.findFirstAvailableDate(monday, calendarHelper);
        assertEquals(LocalDate.of(2026, 3, 4), firstAvailable); // Wednesday
    }

    @Test
    void simulateAllocationSkipsBlockedDatesWithoutModifyingState() {
        AssigneeSchedule schedule = new AssigneeSchedule("user1", "User 1", "DEV", new BigDecimal("8"));

        LocalDate tuesday = LocalDate.of(2026, 3, 3);
        schedule.blockAbsenceDates(Set.of(tuesday));

        LocalDate monday = LocalDate.of(2026, 3, 2);
        AssigneeSchedule.AllocationResult simulated = schedule.simulateAllocation(
                new BigDecimal("16"), monday, calendarHelper);

        // Simulation should skip Tuesday
        assertEquals(monday, simulated.startDate());
        assertEquals(LocalDate.of(2026, 3, 4), simulated.endDate());

        // State should NOT be modified — Monday should still have full capacity
        assertEquals(new BigDecimal("8"), schedule.getAvailableHours(monday));
    }
}
