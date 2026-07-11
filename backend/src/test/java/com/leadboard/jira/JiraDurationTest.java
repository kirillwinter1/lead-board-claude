package com.leadboard.jira;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Проверяет форматтер {@link JiraDuration#format(long)} — Jira-единицы 8h/день, 5d/неделя,
 * пропуск нулевых компонентов, округление секунд до минут вниз.
 */
class JiraDurationTest {

    @Test
    void zeroSecondsFormatsAsZeroMinutes() {
        assertEquals("0m", JiraDuration.format(0));
    }

    @Test
    void sixHours() {
        assertEquals("6h", JiraDuration.format(21600));
    }

    @Test
    void twelveHoursSpillsIntoOneDayFourHours() {
        assertEquals("1d 4h", JiraDuration.format(43200));
    }

    @Test
    void oneWeek() {
        assertEquals("1w", JiraDuration.format(144000));
    }

    @Test
    void twoWeeksThreeDays() {
        // 2w = 288000s, 3d = 86400s → 374400s
        assertEquals("2w 3d", JiraDuration.format(374400));
    }

    @Test
    void minutesOnly() {
        assertEquals("45m", JiraDuration.format(2700));
    }

    @Test
    void fullChainWeeksDaysHoursMinutes() {
        // 2w + 4d + 6h + 45m = 288000 + 115200 + 21600 + 2700 = 427500s
        assertEquals("2w 4d 6h 45m", JiraDuration.format(427500));
    }

    @Test
    void leftoverSecondsRoundDownToMinutes() {
        // 6h + 59s → seconds below a minute are dropped
        assertEquals("6h", JiraDuration.format(21659));
    }
}
