package com.leadboard.calendar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkCalendarServiceTest {

    @Mock
    private CalendarApiClient calendarApiClient;

    @Mock
    private CalendarHolidayRepository holidayRepository;

    private CalendarProperties calendarProperties;
    private WorkCalendarService workCalendarService;

    @BeforeEach
    void setUp() {
        calendarProperties = new CalendarProperties();
        calendarProperties.setSource("api");
        calendarProperties.setCountry("RU");
        calendarProperties.setCacheTtl(86400);

        workCalendarService = new WorkCalendarService(
                calendarApiClient,
                holidayRepository,
                calendarProperties
        );
    }

    // ==================== isWorkday Tests ====================

    @Test
    void isWorkdayReturnsTrueForRegularWorkday() {
        // Понедельник 13 января 2025 - обычный рабочий день
        LocalDate monday = LocalDate.of(2025, 1, 13);

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        boolean result = workCalendarService.isWorkday(monday);

        assertTrue(result);
    }

    @Test
    void isWorkdayReturnsFalseForWeekend() {
        // Суббота 11 января 2025
        LocalDate saturday = LocalDate.of(2025, 1, 11);

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        boolean result = workCalendarService.isWorkday(saturday);

        assertFalse(result);
    }

    @Test
    void isWorkdayReturnsFalseForHoliday() {
        // 1 января 2025 - праздник
        LocalDate newYear = LocalDate.of(2025, 1, 1);

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        nonWorkingDays.add(newYear); // Добавляем праздник
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        boolean result = workCalendarService.isWorkday(newYear);

        assertFalse(result);
    }

    // ==================== countWorkdays Tests ====================

    @Test
    void countWorkdaysReturnsCorrectCountForWeek() {
        // Неделя с 13 по 17 января 2025 (пн-пт) = 5 рабочих дней
        LocalDate from = LocalDate.of(2025, 1, 13);
        LocalDate to = LocalDate.of(2025, 1, 17);

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        int result = workCalendarService.countWorkdays(from, to);

        assertEquals(5, result);
    }

    @Test
    void countWorkdaysExcludesWeekends() {
        // С 11 по 17 января 2025 (сб-пт) = 5 рабочих дней (сб и вс не считаются)
        LocalDate from = LocalDate.of(2025, 1, 11);
        LocalDate to = LocalDate.of(2025, 1, 17);

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        int result = workCalendarService.countWorkdays(from, to);

        assertEquals(5, result);
    }

    @Test
    void countWorkdaysReturnsZeroWhenFromAfterTo() {
        LocalDate from = LocalDate.of(2025, 1, 20);
        LocalDate to = LocalDate.of(2025, 1, 10);

        int result = workCalendarService.countWorkdays(from, to);

        assertEquals(0, result);
    }

    @Test
    void countWorkdaysIncludesBothDates() {
        // Один рабочий день (понедельник)
        LocalDate date = LocalDate.of(2025, 1, 13);

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        int result = workCalendarService.countWorkdays(date, date);

        assertEquals(1, result);
    }

    @Test
    void countWorkdaysExcludesHolidays() {
        // Первая неделя января с праздниками
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 10);

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        // Добавляем новогодние праздники 1-8 января
        for (int day = 1; day <= 8; day++) {
            nonWorkingDays.add(LocalDate.of(2025, 1, day));
        }
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        int result = workCalendarService.countWorkdays(from, to);

        // 9, 10 января - чт, пт = 2 рабочих дня
        assertEquals(2, result);
    }

    // ==================== addWorkdays Tests ====================

    @Test
    void addWorkdaysAddsCorrectNumberOfDays() {
        LocalDate start = LocalDate.of(2025, 1, 13); // Понедельник

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        // Добавляем 5 рабочих дней: 14(вт)+1, 15(ср)+2, 16(чт)+3, 17(пт)+4, 18-19 пропуск, 20(пн)+5
        LocalDate result = workCalendarService.addWorkdays(start, 5);

        // Должны получить понедельник 20 января
        assertEquals(LocalDate.of(2025, 1, 20), result);
    }

    @Test
    void addWorkdaysSkipsWeekends() {
        LocalDate friday = LocalDate.of(2025, 1, 10); // Пятница

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        // Добавляем 1 рабочий день
        LocalDate result = workCalendarService.addWorkdays(friday, 1);

        // Должны получить понедельник 13 января (пропускаем сб 11 и вс 12)
        assertEquals(LocalDate.of(2025, 1, 13), result);
    }

    @Test
    void addWorkdaysSkipsHolidays() {
        LocalDate start = LocalDate.of(2025, 1, 9); // Четверг после праздников

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        // Добавляем 2 рабочих дня
        LocalDate result = workCalendarService.addWorkdays(start, 2);

        // 10 (пт) + пропускаем 11-12 (сб-вс) = 13 (пн)
        assertEquals(LocalDate.of(2025, 1, 13), result);
    }

    @Test
    void addWorkdaysReturnsStartDateWhenZeroDays() {
        LocalDate start = LocalDate.of(2025, 1, 13);

        LocalDate result = workCalendarService.addWorkdays(start, 0);

        assertEquals(start, result);
    }

    @Test
    void addWorkdaysStartsFromNextWorkdayIfStartIsHoliday() {
        LocalDate saturday = LocalDate.of(2025, 1, 11); // Суббота

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        // Добавляем 1 рабочий день
        LocalDate result = workCalendarService.addWorkdays(saturday, 1);

        // Начинаем с понедельника 13, добавляем 1 = вторник 14
        assertEquals(LocalDate.of(2025, 1, 14), result);
    }

    // ==================== Cross-year Tests ====================

    @Test
    void countWorkdaysAcrossYears() {
        LocalDate from = LocalDate.of(2024, 12, 30);
        LocalDate to = LocalDate.of(2025, 1, 3);

        Set<LocalDate> nonWorkingDays2024 = new HashSet<>();
        nonWorkingDays2024.add(LocalDate.of(2024, 12, 28)); // Сб
        nonWorkingDays2024.add(LocalDate.of(2024, 12, 29)); // Вс

        Set<LocalDate> nonWorkingDays2025 = new HashSet<>();
        // 1-3 января - праздники
        nonWorkingDays2025.add(LocalDate.of(2025, 1, 1));
        nonWorkingDays2025.add(LocalDate.of(2025, 1, 2));
        nonWorkingDays2025.add(LocalDate.of(2025, 1, 3));

        when(calendarApiClient.fetchNonWorkingDays(2024, "RU")).thenReturn(nonWorkingDays2024);
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays2025);

        int result = workCalendarService.countWorkdays(from, to);

        // 30 (пн), 31 (вт) = 2 рабочих дня
        assertEquals(2, result);
    }

    // ==================== Cache Tests ====================

    @Test
    void cacheIsUsedForRepeatedCalls() {
        LocalDate date = LocalDate.of(2025, 1, 13);

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        // Первый вызов
        workCalendarService.isWorkday(date);
        // Второй вызов
        workCalendarService.isWorkday(date);
        // Третий вызов
        workCalendarService.isWorkday(date);

        // API должен быть вызван только один раз
        verify(calendarApiClient, times(1)).fetchNonWorkingDays(2025, "RU");
    }

    @Test
    void clearCacheRemovesCachedData() {
        LocalDate date = LocalDate.of(2025, 1, 13);

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        // Первый вызов - загрузка в кэш
        workCalendarService.isWorkday(date);

        // Очищаем кэш
        workCalendarService.clearCache();

        // Второй вызов - должен снова загрузить
        workCalendarService.isWorkday(date);

        // API должен быть вызван дважды
        verify(calendarApiClient, times(2)).fetchNonWorkingDays(2025, "RU");
    }

    // ==================== getWorkdaysInfo Tests ====================

    @Test
    void getWorkdaysInfoReturnsCorrectStats() {
        LocalDate from = LocalDate.of(2025, 1, 13);
        LocalDate to = LocalDate.of(2025, 1, 19);

        Set<LocalDate> nonWorkingDays = createWeekendsForJanuary2025();
        when(calendarApiClient.fetchNonWorkingDays(2025, "RU")).thenReturn(nonWorkingDays);

        var result = workCalendarService.getWorkdaysInfo(from, to);

        assertEquals(from, result.from());
        assertEquals(to, result.to());
        assertEquals("RU", result.country());
        assertEquals(7, result.totalDays());
        assertEquals(5, result.workdays()); // пн-пт
        assertEquals(2, result.weekends()); // сб, вс
        assertEquals(0, result.holidays()); // нет праздников в этом периоде
        assertEquals(5, result.workdayDates().size());
    }

    // ==================== Helper Methods ====================

    private Set<LocalDate> createWeekendsForJanuary2025() {
        Set<LocalDate> weekends = new HashSet<>();
        LocalDate date = LocalDate.of(2025, 1, 1);

        while (date.getMonthValue() == 1) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                weekends.add(date);
            }
            date = date.plusDays(1);
        }

        return weekends;
    }
}
