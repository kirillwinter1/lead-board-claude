package com.leadboard.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * Клиент для получения производственного календаря из внешнего API.
 * Использует xmlcalendar.ru API для получения данных о выходных и праздниках РФ.
 */
@Component
public class CalendarApiClient {

    private static final Logger log = LoggerFactory.getLogger(CalendarApiClient.class);

    // xmlcalendar.ru - возвращает JSON с выходными днями по месяцам
    private static final String XMLCALENDAR_API_URL = "https://xmlcalendar.ru/data/ru";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public CalendarApiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Получает список нерабочих дней (праздников и выходных) для указанного года.
     *
     * @param year год
     * @param country код страны (пока поддерживается только RU)
     * @return Set дат нерабочих дней
     */
    public Set<LocalDate> fetchNonWorkingDays(int year, String country) {
        if (!"RU".equals(country)) {
            log.warn("Country {} is not supported, only RU is available. Returning weekends only.", country);
            return getWeekendsForYear(year);
        }

        try {
            log.info("Fetching calendar data from xmlcalendar.ru for year {}", year);

            String url = XMLCALENDAR_API_URL + "/" + year + "/calendar.json";

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null || response.isEmpty()) {
                log.warn("Empty response from xmlcalendar.ru, falling back to weekends");
                return getWeekendsForYear(year);
            }

            return parseXmlCalendarResponse(response, year);

        } catch (WebClientResponseException e) {
            log.error("Error fetching calendar from xmlcalendar.ru: {} - {}", e.getStatusCode(), e.getMessage());
            return getWeekendsForYear(year);
        } catch (Exception e) {
            log.error("Unexpected error fetching calendar: {}", e.getMessage(), e);
            return getWeekendsForYear(year);
        }
    }

    /**
     * Парсит ответ от xmlcalendar.ru API.
     * Формат:
     * {
     *   "year": 2025,
     *   "months": [
     *     { "month": 1, "days": "1,2,3,4,5,6,7,8,11,12,18,19,25,26" },
     *     ...
     *   ]
     * }
     *
     * days содержит номера выходных/праздничных дней
     * * - сокращённый день (предпраздничный) - игнорируем
     * + - перенесённый выходной - учитываем как выходной
     */
    private Set<LocalDate> parseXmlCalendarResponse(String response, int year) {
        Set<LocalDate> nonWorkingDays = new HashSet<>();

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode months = root.get("months");

            if (months == null || !months.isArray()) {
                log.warn("Invalid response format from xmlcalendar.ru");
                return getWeekendsForYear(year);
            }

            for (JsonNode monthNode : months) {
                int month = monthNode.get("month").asInt();
                String daysStr = monthNode.get("days").asText();

                if (daysStr == null || daysStr.isEmpty()) {
                    continue;
                }

                // Парсим дни: "1,2,3,4,5,6,7,8,11,12,18,19,25,26"
                // Убираем * (сокращённые дни - они рабочие)
                // Оставляем + (перенесённые выходные)
                String[] days = daysStr.split(",");
                for (String dayStr : days) {
                    String cleanDay = dayStr.trim();

                    // Пропускаем сокращённые дни (с *)
                    if (cleanDay.endsWith("*")) {
                        continue;
                    }

                    // Убираем + для перенесённых выходных
                    cleanDay = cleanDay.replace("+", "");

                    try {
                        int day = Integer.parseInt(cleanDay);
                        LocalDate date = LocalDate.of(year, month, day);
                        nonWorkingDays.add(date);
                    } catch (NumberFormatException e) {
                        log.debug("Could not parse day: {} in month {}", dayStr, month);
                    }
                }
            }

            log.info("Parsed {} non-working days for year {} from xmlcalendar.ru", nonWorkingDays.size(), year);
            return nonWorkingDays;

        } catch (Exception e) {
            log.error("Error parsing xmlcalendar.ru response: {}", e.getMessage(), e);
            return getWeekendsForYear(year);
        }
    }

    /**
     * Возвращает все выходные (сб, вс) для указанного года.
     * Используется как fallback при недоступности API.
     */
    private Set<LocalDate> getWeekendsForYear(int year) {
        Set<LocalDate> weekends = new HashSet<>();
        LocalDate date = LocalDate.of(year, 1, 1);

        while (date.getYear() == year) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                weekends.add(date);
            }
            date = date.plusDays(1);
        }

        log.info("Generated {} weekend days for year {} (API fallback)", weekends.size(), year);
        return weekends;
    }

    /**
     * Проверяет доступность API.
     */
    public boolean isApiAvailable() {
        try {
            int currentYear = LocalDate.now().getYear();
            String url = XMLCALENDAR_API_URL + "/" + currentYear + "/calendar.json";

            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return response != null && response.contains("\"year\"");
        } catch (Exception e) {
            log.debug("Calendar API is not available: {}", e.getMessage());
            return false;
        }
    }
}
