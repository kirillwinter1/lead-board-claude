package com.leadboard.quality;

/**
 * Categories grouping data quality rules by the kind of problem they detect.
 * Each category has a human-readable label surfaced in the API and UI.
 */
public enum DataQualityCategory {

    TIME_LOGGING("Time Logging"),
    STATUS_CONSISTENCY("Status Consistency"),
    ESTIMATES("Estimates"),
    TEAM("Team"),
    ASSIGNEE("Assignee"),
    DUE_DATES("Due Dates"),
    HIERARCHY("Hierarchy"),
    DEPENDENCIES("Dependencies"),
    STALENESS("Staleness"),
    RICE("RICE"),
    BUG_SLA("Bug SLA"),
    CONTENT("Content");

    private final String label;

    DataQualityCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
