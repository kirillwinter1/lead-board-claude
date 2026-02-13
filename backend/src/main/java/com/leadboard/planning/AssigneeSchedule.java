package com.leadboard.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks assignee availability across days.
 * Supports partial day allocation (e.g., 3h on story A + 5h on story B in same day).
 */
public class AssigneeSchedule {

    private final String accountId;
    private final String displayName;
    private final String roleCode; // Dynamic role code (e.g., "SA", "DEV", "QA")
    private final BigDecimal effectiveHoursPerDay;

    // Tracks used hours per day: date -> hours already allocated
    private final Map<LocalDate, BigDecimal> usedHours = new HashMap<>();

    // Tracks total assigned hours for utilization stats
    private BigDecimal totalAssignedHours = BigDecimal.ZERO;

    public AssigneeSchedule(String accountId, String displayName, String roleCode, BigDecimal effectiveHoursPerDay) {
        this.accountId = accountId;
        this.displayName = displayName;
        this.roleCode = roleCode;
        this.effectiveHoursPerDay = effectiveHoursPerDay;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public BigDecimal getEffectiveHoursPerDay() {
        return effectiveHoursPerDay;
    }

    public BigDecimal getTotalAssignedHours() {
        return totalAssignedHours;
    }

    /**
     * Returns available hours on a specific date.
     */
    public BigDecimal getAvailableHours(LocalDate date) {
        BigDecimal used = usedHours.getOrDefault(date, BigDecimal.ZERO);
        return effectiveHoursPerDay.subtract(used).max(BigDecimal.ZERO);
    }

    /**
     * Reserves hours on a specific date.
     *
     * @param date  the date to reserve
     * @param hours hours to reserve
     * @throws IllegalArgumentException if not enough hours available
     */
    public void reserveHours(LocalDate date, BigDecimal hours) {
        BigDecimal available = getAvailableHours(date);
        if (hours.compareTo(available) > 0) {
            throw new IllegalArgumentException(
                    String.format("Cannot reserve %.1fh on %s for %s - only %.1fh available",
                            hours, date, displayName, available));
        }

        BigDecimal currentUsed = usedHours.getOrDefault(date, BigDecimal.ZERO);
        usedHours.put(date, currentUsed.add(hours));
        totalAssignedHours = totalAssignedHours.add(hours);
    }

    /**
     * Finds the first date (on or after startDate) where this assignee has available hours.
     *
     * @param startDate earliest possible date
     * @param calendar  work calendar for skipping weekends/holidays
     * @return first date with available hours
     */
    public LocalDate findFirstAvailableDate(LocalDate startDate, WorkCalendarHelper calendar) {
        LocalDate date = calendar.ensureWorkday(startDate);
        int maxIterations = 365; // Safety limit

        for (int i = 0; i < maxIterations; i++) {
            if (getAvailableHours(date).compareTo(BigDecimal.ZERO) > 0) {
                return date;
            }
            date = calendar.nextWorkday(date);
        }

        // Fallback - should not happen in normal use
        return date;
    }

    /**
     * Returns daily load map for utilization visualization.
     */
    public Map<LocalDate, BigDecimal> getDailyLoad() {
        return new HashMap<>(usedHours);
    }

    /**
     * Helper interface for work calendar operations.
     */
    public interface WorkCalendarHelper {
        /**
         * Returns the same date if it's a workday, otherwise next workday.
         */
        LocalDate ensureWorkday(LocalDate date);

        /**
         * Returns the next workday after the given date.
         */
        LocalDate nextWorkday(LocalDate date);

        /**
         * Checks if date is a workday.
         */
        boolean isWorkday(LocalDate date);
    }

    /**
     * Result of allocating hours to this assignee.
     */
    public record AllocationResult(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal hoursAllocated
    ) {}

    /**
     * Allocates hours to this assignee starting from a given date.
     * Handles partial day allocation.
     *
     * @param hoursNeeded hours to allocate
     * @param startAfter  earliest start date
     * @param calendar    work calendar
     * @return allocation result with start and end dates
     */
    public AllocationResult allocateHours(BigDecimal hoursNeeded, LocalDate startAfter, WorkCalendarHelper calendar) {
        if (hoursNeeded.compareTo(BigDecimal.ZERO) <= 0) {
            return new AllocationResult(startAfter, startAfter, BigDecimal.ZERO);
        }

        BigDecimal remaining = hoursNeeded;
        LocalDate currentDate = calendar.ensureWorkday(startAfter);
        LocalDate startDate = null;
        LocalDate endDate = null;

        int maxIterations = 365; // Safety limit

        for (int i = 0; i < maxIterations && remaining.compareTo(BigDecimal.ZERO) > 0; i++) {
            // Ensure we're on a workday
            currentDate = calendar.ensureWorkday(currentDate);

            BigDecimal available = getAvailableHours(currentDate);

            if (available.compareTo(BigDecimal.ZERO) > 0) {
                if (startDate == null) {
                    startDate = currentDate;
                }

                // Use min of (remaining, available)
                BigDecimal toUse = remaining.min(available);
                reserveHours(currentDate, toUse);
                remaining = remaining.subtract(toUse);
                endDate = currentDate;
            }

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                currentDate = calendar.nextWorkday(currentDate);
            }
        }

        return new AllocationResult(
                startDate != null ? startDate : startAfter,
                endDate != null ? endDate : startAfter,
                hoursNeeded.subtract(remaining)
        );
    }

    @Override
    public String toString() {
        return String.format("AssigneeSchedule{%s (%s), %.1fh/day, assigned=%.1fh}",
                displayName, roleCode, effectiveHoursPerDay, totalAssignedHours);
    }
}
