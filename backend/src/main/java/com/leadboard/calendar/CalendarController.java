package com.leadboard.calendar;

import com.leadboard.calendar.dto.WorkdaysResponseDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final WorkCalendarService workCalendarService;
    private final CalendarProperties calendarProperties;

    public CalendarController(WorkCalendarService workCalendarService, CalendarProperties calendarProperties) {
        this.workCalendarService = workCalendarService;
        this.calendarProperties = calendarProperties;
    }

    /**
     * Получить информацию о рабочих днях за период.
     *
     * @param from начальная дата (включительно)
     * @param to конечная дата (включительно)
     * @param country код страны (по умолчанию RU)
     * @return информация о рабочих днях, выходных и праздниках
     */
    @GetMapping("/workdays")
    public ResponseEntity<WorkdaysResponseDto> getWorkdays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String country
    ) {
        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }

        // Ограничиваем период одним годом для производительности
        if (to.toEpochDay() - from.toEpochDay() > 366) {
            return ResponseEntity.badRequest().build();
        }

        String effectiveCountry = country != null ? country : calendarProperties.getCountry();
        WorkdaysResponseDto response = workCalendarService.getWorkdaysInfo(from, to, effectiveCountry);

        return ResponseEntity.ok(response);
    }

    /**
     * Проверить, является ли дата рабочим днём.
     *
     * @param date дата для проверки
     * @param country код страны (по умолчанию RU)
     * @return результат проверки
     */
    @GetMapping("/is-workday")
    public ResponseEntity<Map<String, Object>> isWorkday(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String country
    ) {
        String effectiveCountry = country != null ? country : calendarProperties.getCountry();
        boolean isWorkday = workCalendarService.isWorkday(date, effectiveCountry);

        return ResponseEntity.ok(Map.of(
                "date", date.toString(),
                "country", effectiveCountry,
                "isWorkday", isWorkday
        ));
    }

    /**
     * Подсчитать количество рабочих дней между датами.
     *
     * @param from начальная дата (включительно)
     * @param to конечная дата (включительно)
     * @param country код страны (по умолчанию RU)
     * @return количество рабочих дней
     */
    @GetMapping("/count-workdays")
    public ResponseEntity<Map<String, Object>> countWorkdays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String country
    ) {
        if (from.isAfter(to)) {
            return ResponseEntity.badRequest().build();
        }

        String effectiveCountry = country != null ? country : calendarProperties.getCountry();
        int workdays = workCalendarService.countWorkdays(from, to, effectiveCountry);

        return ResponseEntity.ok(Map.of(
                "from", from.toString(),
                "to", to.toString(),
                "country", effectiveCountry,
                "workdays", workdays
        ));
    }

    /**
     * Вычислить дату через N рабочих дней.
     *
     * @param from начальная дата
     * @param days количество рабочих дней для добавления
     * @param country код страны (по умолчанию RU)
     * @return дата после добавления рабочих дней
     */
    @GetMapping("/add-workdays")
    public ResponseEntity<Map<String, Object>> addWorkdays(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam int days,
            @RequestParam(required = false) String country
    ) {
        if (days < 0 || days > 365) {
            return ResponseEntity.badRequest().build();
        }

        String effectiveCountry = country != null ? country : calendarProperties.getCountry();
        LocalDate resultDate = workCalendarService.addWorkdays(from, days, effectiveCountry);

        return ResponseEntity.ok(Map.of(
                "from", from.toString(),
                "workdaysToAdd", days,
                "country", effectiveCountry,
                "resultDate", resultDate.toString()
        ));
    }

    /**
     * Принудительно обновить кэш календаря.
     *
     * @param year год для обновления
     * @param country код страны (по умолчанию RU)
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshCache(
            @RequestParam int year,
            @RequestParam(required = false) String country
    ) {
        String effectiveCountry = country != null ? country : calendarProperties.getCountry();
        workCalendarService.refreshCache(year, effectiveCountry);

        return ResponseEntity.ok(Map.of(
                "status", "refreshed",
                "year", String.valueOf(year),
                "country", effectiveCountry
        ));
    }

    /**
     * Получить текущую конфигурацию календаря.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "source", calendarProperties.getSource(),
                "country", calendarProperties.getCountry(),
                "cacheTtlSeconds", calendarProperties.getCacheTtl()
        ));
    }
}
