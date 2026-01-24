package com.leadboard.calendar;

import com.leadboard.calendar.dto.HolidayDto;
import com.leadboard.calendar.dto.WorkdaysResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для работы с производственным календарём.
 * Предоставляет методы для расчёта рабочих дней с учётом выходных и праздников.
 */
@Service
public class WorkCalendarService {

    private static final Logger log = LoggerFactory.getLogger(WorkCalendarService.class);

    private final CalendarApiClient calendarApiClient;
    private final CalendarHolidayRepository holidayRepository;
    private final CalendarProperties calendarProperties;

    // Кэш нерабочих дней по годам: country_year -> Set<LocalDate>
    private final Map<String, CachedData> cache = new ConcurrentHashMap<>();

    public WorkCalendarService(
            CalendarApiClient calendarApiClient,
            CalendarHolidayRepository holidayRepository,
            CalendarProperties calendarProperties
    ) {
        this.calendarApiClient = calendarApiClient;
        this.holidayRepository = holidayRepository;
        this.calendarProperties = calendarProperties;
    }

    /**
     * Проверяет, является ли дата рабочим днём.
     */
    public boolean isWorkday(LocalDate date) {
        return isWorkday(date, calendarProperties.getCountry());
    }

    /**
     * Проверяет, является ли дата рабочим днём для указанной страны.
     */
    public boolean isWorkday(LocalDate date, String country) {
        Set<LocalDate> nonWorkingDays = getNonWorkingDays(date.getYear(), country);
        return !nonWorkingDays.contains(date);
    }

    /**
     * Подсчитывает количество рабочих дней между двумя датами (включительно).
     */
    public int countWorkdays(LocalDate from, LocalDate to) {
        return countWorkdays(from, to, calendarProperties.getCountry());
    }

    /**
     * Подсчитывает количество рабочих дней между двумя датами (включительно) для указанной страны.
     */
    public int countWorkdays(LocalDate from, LocalDate to, String country) {
        if (from.isAfter(to)) {
            return 0;
        }

        int count = 0;
        LocalDate current = from;

        // Собираем нерабочие дни для всех затронутых годов
        Set<LocalDate> allNonWorkingDays = new HashSet<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            allNonWorkingDays.addAll(getNonWorkingDays(year, country));
        }

        while (!current.isAfter(to)) {
            if (!allNonWorkingDays.contains(current)) {
                count++;
            }
            current = current.plusDays(1);
        }

        return count;
    }

    /**
     * Добавляет указанное количество рабочих дней к дате.
     *
     * @param startDate начальная дата
     * @param workdays количество рабочих дней для добавления
     * @return дата после добавления рабочих дней
     */
    public LocalDate addWorkdays(LocalDate startDate, int workdays) {
        return addWorkdays(startDate, workdays, calendarProperties.getCountry());
    }

    /**
     * Добавляет указанное количество рабочих дней к дате для указанной страны.
     */
    public LocalDate addWorkdays(LocalDate startDate, int workdays, String country) {
        if (workdays <= 0) {
            return startDate;
        }

        LocalDate current = startDate;
        int addedDays = 0;
        int lastYear = current.getYear();

        // Если начальная дата - нерабочий день, переходим на следующий рабочий
        Set<LocalDate> nonWorkingDays = getNonWorkingDays(lastYear, country);
        while (nonWorkingDays.contains(current)) {
            current = current.plusDays(1);
            // Проверяем переход года
            if (current.getYear() != lastYear) {
                lastYear = current.getYear();
                nonWorkingDays = getNonWorkingDays(lastYear, country);
            }
        }

        while (addedDays < workdays) {
            current = current.plusDays(1);

            // Проверяем переход года для обновления кэша (простое сравнение int вместо equals на Set)
            if (current.getYear() != lastYear) {
                lastYear = current.getYear();
                nonWorkingDays = getNonWorkingDays(lastYear, country);
            }

            if (!nonWorkingDays.contains(current)) {
                addedDays++;
            }
        }

        return current;
    }

    /**
     * Получает информацию о рабочих днях за период.
     */
    public WorkdaysResponseDto getWorkdaysInfo(LocalDate from, LocalDate to) {
        return getWorkdaysInfo(from, to, calendarProperties.getCountry());
    }

    /**
     * Получает информацию о рабочих днях за период для указанной страны.
     */
    public WorkdaysResponseDto getWorkdaysInfo(LocalDate from, LocalDate to, String country) {
        // Собираем нерабочие дни
        Set<LocalDate> allNonWorkingDays = new HashSet<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            allNonWorkingDays.addAll(getNonWorkingDays(year, country));
        }

        List<LocalDate> workdayDates = new ArrayList<>();
        List<HolidayDto> holidayList = new ArrayList<>();
        int weekends = 0;
        int holidays = 0;

        LocalDate current = from;
        while (!current.isAfter(to)) {
            if (allNonWorkingDays.contains(current)) {
                DayOfWeek dayOfWeek = current.getDayOfWeek();
                if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                    weekends++;
                } else {
                    holidays++;
                    holidayList.add(new HolidayDto(current, getHolidayName(current, country)));
                }
            } else {
                workdayDates.add(current);
            }
            current = current.plusDays(1);
        }

        int totalDays = (int) (to.toEpochDay() - from.toEpochDay()) + 1;

        return new WorkdaysResponseDto(
                from,
                to,
                country,
                totalDays,
                workdayDates.size(),
                weekends,
                holidays,
                workdayDates,
                holidayList
        );
    }

    /**
     * Получает набор нерабочих дней для указанного года и страны.
     */
    private Set<LocalDate> getNonWorkingDays(int year, String country) {
        String cacheKey = country + "_" + year;

        CachedData cached = cache.get(cacheKey);
        if (cached != null && !cached.isExpired(calendarProperties.getCacheTtl())) {
            return cached.data;
        }

        Set<LocalDate> nonWorkingDays = loadNonWorkingDays(year, country);
        cache.put(cacheKey, new CachedData(nonWorkingDays));

        return nonWorkingDays;
    }

    /**
     * Загружает нерабочие дни из настроенного источника.
     */
    private Set<LocalDate> loadNonWorkingDays(int year, String country) {
        String source = calendarProperties.getSource();

        return switch (source.toLowerCase()) {
            case "api" -> loadFromApi(year, country);
            case "database" -> loadFromDatabase(year, country);
            case "file" -> loadFromFile(year, country);
            default -> {
                log.warn("Unknown calendar source: {}, falling back to API", source);
                yield loadFromApi(year, country);
            }
        };
    }

    private Set<LocalDate> loadFromApi(int year, String country) {
        Set<LocalDate> days = calendarApiClient.fetchNonWorkingDays(year, country);

        // Сохраняем в БД для кэширования (для случая если API станет недоступен)
        saveToDatabase(days, year, country);

        return days;
    }

    @Transactional(readOnly = true)
    private Set<LocalDate> loadFromDatabase(int year, String country) {
        Set<LocalDate> dbDays = holidayRepository.findHolidayDatesByCountryAndDateBetween(
                country,
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31)
        );

        if (dbDays.isEmpty()) {
            log.warn("No calendar data in database for {} {}, falling back to API", country, year);
            return loadFromApi(year, country);
        }

        // Добавляем выходные дни
        dbDays.addAll(getWeekends(year));

        return dbDays;
    }

    private Set<LocalDate> loadFromFile(int year, String country) {
        // TODO: Реализовать загрузку из файла
        log.warn("File source not implemented yet, falling back to API");
        return loadFromApi(year, country);
    }

    @Transactional
    private void saveToDatabase(Set<LocalDate> nonWorkingDays, int year, String country) {
        try {
            // Удаляем старые записи за год
            holidayRepository.deleteByCountryAndYear(country, year);

            // Сохраняем только праздники (не выходные)
            List<CalendarHolidayEntity> holidays = nonWorkingDays.stream()
                    .filter(date -> {
                        DayOfWeek dow = date.getDayOfWeek();
                        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
                    })
                    .map(date -> {
                        CalendarHolidayEntity entity = new CalendarHolidayEntity();
                        entity.setDate(date);
                        entity.setCountry(country);
                        entity.setYear(year);
                        return entity;
                    })
                    .toList();

            if (!holidays.isEmpty()) {
                holidayRepository.saveAll(holidays);
                log.info("Saved {} holidays to database for {} {}", holidays.size(), country, year);
            }
        } catch (Exception e) {
            log.error("Failed to save holidays to database: {}", e.getMessage());
        }
    }

    private Set<LocalDate> getWeekends(int year) {
        Set<LocalDate> weekends = new HashSet<>();
        LocalDate date = LocalDate.of(year, 1, 1);

        while (date.getYear() == year) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                weekends.add(date);
            }
            date = date.plusDays(1);
        }

        return weekends;
    }

    private String getHolidayName(LocalDate date, String country) {
        // Базовые названия праздников РФ
        if ("RU".equals(country)) {
            int month = date.getMonthValue();
            int day = date.getDayOfMonth();

            if (month == 1 && day >= 1 && day <= 8) return "Новогодние каникулы";
            if (month == 2 && day == 23) return "День защитника Отечества";
            if (month == 3 && day == 8) return "Международный женский день";
            if (month == 5 && day == 1) return "Праздник Весны и Труда";
            if (month == 5 && day == 9) return "День Победы";
            if (month == 6 && day == 12) return "День России";
            if (month == 11 && day == 4) return "День народного единства";
        }

        return null;
    }

    /**
     * Принудительно обновляет кэш календаря.
     */
    public void refreshCache(int year, String country) {
        String cacheKey = country + "_" + year;
        cache.remove(cacheKey);
        getNonWorkingDays(year, country);
        log.info("Calendar cache refreshed for {} {}", country, year);
    }

    /**
     * Очищает весь кэш.
     */
    public void clearCache() {
        cache.clear();
        log.info("Calendar cache cleared");
    }

    /**
     * Внутренний класс для хранения кэшированных данных с timestamp.
     */
    private static class CachedData {
        final Set<LocalDate> data;
        final long timestamp;

        CachedData(Set<LocalDate> data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired(long ttlSeconds) {
            return System.currentTimeMillis() - timestamp > ttlSeconds * 1000;
        }
    }
}
