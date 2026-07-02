package com.leadboard.quality.fix.dto;

import java.util.List;

/**
 * A user input the fix requires (rendered in the Fix modal).
 *
 * @param name         request param name (key in {@link FixRequest#params()})
 * @param type         one of "select", "date", "number"
 * @param label        field label
 * @param required     whether the value is mandatory before Apply
 * @param defaultValue optional default value
 * @param min          optional minimum (for "number")
 * @param step         optional step (for "number")
 * @param options      options for "select" (null otherwise)
 */
public record FixInput(
        String name,
        String type,
        String label,
        boolean required,
        Object defaultValue,
        Double min,
        Double step,
        List<Option> options
) {
    /**
     * A select option: machine value + display label, plus an optional {@code color} (hex,
     * e.g. a team color) so the UI can render a color swatch. {@code color} is null for options
     * that have no color (e.g. member/assignee selects).
     */
    public record Option(String value, String label, String color) {
        /** Convenience constructor for options without a color. */
        public Option(String value, String label) {
            this(value, label, null);
        }
    }

    public static FixInput select(String name, String label, boolean required, List<Option> options, String defaultValue) {
        return new FixInput(name, "select", label, required, defaultValue, null, null, options);
    }

    public static FixInput date(String name, String label, boolean required, String defaultValue) {
        return new FixInput(name, "date", label, required, defaultValue, null, null, null);
    }

    public static FixInput number(String name, String label, boolean required, Object defaultValue, Double min, Double step) {
        return new FixInput(name, "number", label, required, defaultValue, min, step, null);
    }
}
