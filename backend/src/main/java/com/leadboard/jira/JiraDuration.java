package com.leadboard.jira;

/**
 * Форматирование длительности в Jira-строку вида {@code "2w 4d 6h 45m"} (F90 — управление
 * остатком Remaining Estimate).
 *
 * <p>Используются рабочие единицы Jira по умолчанию: 1m = 60s, 1h = 3600s, 1d = 8h,
 * 1w = 5d = 40h. Если у Jira-инстанса hours/day ≠ 8, локальный формат может слегка
 * разойтись с Jira — sync выравнивает (Jira — источник истины).</p>
 */
public final class JiraDuration {

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final long SECONDS_PER_DAY = 8L * SECONDS_PER_HOUR;   // 8h = 28800s
    private static final long SECONDS_PER_WEEK = 5L * SECONDS_PER_DAY;   // 5d = 40h = 144000s

    private JiraDuration() {
    }

    /**
     * Форматирует секунды в Jira-строку {@code "2w 4d 6h 45m"}. Компоненты собираются
     * в порядке недели → дни → часы → минуты, нулевые пропускаются. Остаток секунд
     * округляется до минут вниз. Если всё в ноль — возвращается {@code "0m"}.
     */
    public static String format(long seconds) {
        long rem = seconds;

        long weeks = rem / SECONDS_PER_WEEK;
        rem %= SECONDS_PER_WEEK;
        long days = rem / SECONDS_PER_DAY;
        rem %= SECONDS_PER_DAY;
        long hours = rem / SECONDS_PER_HOUR;
        rem %= SECONDS_PER_HOUR;
        long minutes = rem / SECONDS_PER_MINUTE;

        StringBuilder sb = new StringBuilder();
        appendPart(sb, weeks, "w");
        appendPart(sb, days, "d");
        appendPart(sb, hours, "h");
        appendPart(sb, minutes, "m");

        return sb.isEmpty() ? "0m" : sb.toString();
    }

    private static void appendPart(StringBuilder sb, long value, String unit) {
        if (value <= 0) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(value).append(unit);
    }
}
