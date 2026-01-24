package com.leadboard.quality;

/**
 * Severity levels for data quality violations.
 */
public enum DataQualitySeverity {
    /**
     * Blocking error - prevents planning, shown in red.
     */
    ERROR,

    /**
     * Warning - indicates a problem but doesn't block, shown in yellow.
     */
    WARNING,

    /**
     * Informational - recommendation only, shown in gray.
     */
    INFO
}
