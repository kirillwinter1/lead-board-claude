package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Расчёт AutoScore — автоматического приоритета эпика для планирования.
 *
 * Факторы и их веса:
 * - Статус (20): In Progress = 20, другие = 0
 * - Прогресс (15): процент выполнения * 15
 * - Due date (25): экспоненциальный рост к дедлайну
 * - Priority (15): Highest=15, High=10, Medium=5, Low=0
 * - Размер (10): инверсия от estimate (меньше = больше)
 * - Возраст (10): логарифм от дней с создания
 * - Ручной boost: любое значение для ручной сортировки
 *
 * Итого: максимум 100 баллов
 */
@Service
public class AutoScoreCalculator {

    private static final Logger log = LoggerFactory.getLogger(AutoScoreCalculator.class);

    // Веса факторов
    private static final BigDecimal WEIGHT_STATUS = new BigDecimal("20");
    private static final BigDecimal WEIGHT_PROGRESS = new BigDecimal("15");
    private static final BigDecimal WEIGHT_DUE_DATE = new BigDecimal("25");
    private static final BigDecimal WEIGHT_PRIORITY = new BigDecimal("15");
    private static final BigDecimal WEIGHT_SIZE = new BigDecimal("10");
    private static final BigDecimal WEIGHT_AGE = new BigDecimal("10");
    private static final BigDecimal WEIGHT_MANUAL_BOOST = new BigDecimal("5");

    // Статусы "в работе"
    private static final String[] IN_PROGRESS_STATUSES = {
            "In Progress", "В работе", "In Review", "На ревью",
            "In Development", "Development", "Testing", "На тестировании"
    };

    /**
     * Рассчитывает AutoScore для эпика.
     *
     * @param epic сущность эпика
     * @return score от 0 до 100
     */
    public BigDecimal calculate(JiraIssueEntity epic) {
        Map<String, BigDecimal> factors = calculateFactors(epic);
        return factors.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Рассчитывает отдельные факторы AutoScore.
     *
     * @param epic сущность эпика
     * @return карта фактор -> значение
     */
    public Map<String, BigDecimal> calculateFactors(JiraIssueEntity epic) {
        Map<String, BigDecimal> factors = new LinkedHashMap<>();

        factors.put("status", calculateStatusScore(epic));
        factors.put("progress", calculateProgressScore(epic));
        factors.put("dueDate", calculateDueDateScore(epic));
        factors.put("priority", calculatePriorityScore(epic));
        factors.put("size", calculateSizeScore(epic));
        factors.put("age", calculateAgeScore(epic));
        factors.put("manualBoost", calculateManualBoostScore(epic));

        return factors;
    }

    /**
     * Статус: In Progress = 20, иначе 0
     */
    private BigDecimal calculateStatusScore(JiraIssueEntity epic) {
        String status = epic.getStatus();
        if (status == null) {
            return BigDecimal.ZERO;
        }

        for (String inProgressStatus : IN_PROGRESS_STATUSES) {
            if (status.equalsIgnoreCase(inProgressStatus)) {
                return WEIGHT_STATUS;
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * Прогресс: (logged / estimate) * 15
     * Чем ближе к завершению, тем выше приоритет (быстрее закрыть)
     */
    private BigDecimal calculateProgressScore(JiraIssueEntity epic) {
        Long estimate = epic.getOriginalEstimateSeconds();
        Long logged = epic.getTimeSpentSeconds();

        if (estimate == null || estimate == 0) {
            return BigDecimal.ZERO;
        }

        long loggedValue = logged != null ? logged : 0;
        double progress = Math.min(1.0, (double) loggedValue / estimate);

        return BigDecimal.valueOf(progress)
                .multiply(WEIGHT_PROGRESS)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Due date: экспоненциальный рост к дедлайну.
     * - Просроченный: 25 (максимум)
     * - До 7 дней: 20-25
     * - До 14 дней: 15-20
     * - До 30 дней: 10-15
     * - Более 30 дней: 0-10
     * - Нет due date: 0
     */
    private BigDecimal calculateDueDateScore(JiraIssueEntity epic) {
        LocalDate dueDate = epic.getDueDate();
        if (dueDate == null) {
            return BigDecimal.ZERO;
        }

        LocalDate today = LocalDate.now();
        long daysUntilDue = ChronoUnit.DAYS.between(today, dueDate);

        if (daysUntilDue < 0) {
            // Просроченный
            return WEIGHT_DUE_DATE;
        } else if (daysUntilDue <= 7) {
            // До 7 дней: 20-25
            double score = 20 + (7 - daysUntilDue) * 0.71; // линейная интерполяция
            return BigDecimal.valueOf(Math.min(score, 25)).setScale(2, RoundingMode.HALF_UP);
        } else if (daysUntilDue <= 14) {
            // До 14 дней: 15-20
            double score = 15 + (14 - daysUntilDue) * 0.71;
            return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
        } else if (daysUntilDue <= 30) {
            // До 30 дней: 10-15
            double score = 10 + (30 - daysUntilDue) * 0.31;
            return BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
        } else if (daysUntilDue <= 90) {
            // До 90 дней: 0-10
            double score = 10 * (90 - daysUntilDue) / 60.0;
            return BigDecimal.valueOf(Math.max(0, score)).setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    /**
     * Priority: Highest=15, High=10, Medium=5, Low=2, Lowest=0
     */
    private BigDecimal calculatePriorityScore(JiraIssueEntity epic) {
        String priority = epic.getPriority();
        if (priority == null) {
            return BigDecimal.ZERO;
        }

        return switch (priority.toLowerCase()) {
            case "highest", "blocker", "критический" -> WEIGHT_PRIORITY;
            case "high", "critical", "высокий" -> new BigDecimal("10");
            case "medium", "major", "средний" -> new BigDecimal("5");
            case "low", "minor", "низкий" -> new BigDecimal("2");
            case "lowest", "trivial", "минимальный" -> BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Размер: инверсия от estimate (меньше эпик = выше приоритет, быстрее закрыть).
     * Формула: 10 * (1 - log(estimate_days) / log(max_days))
     * где max_days = 90 (для нормализации)
     */
    private BigDecimal calculateSizeScore(JiraIssueEntity epic) {
        // Используем rough estimate если есть, иначе original estimate
        BigDecimal totalDays = getTotalEstimateDays(epic);

        if (totalDays == null || totalDays.compareTo(BigDecimal.ZERO) <= 0) {
            // Нет оценки — средний балл
            return new BigDecimal("5");
        }

        double days = totalDays.doubleValue();
        double maxDays = 90.0;

        if (days >= maxDays) {
            return BigDecimal.ZERO;
        }

        // Логарифмическая шкала: маленькие эпики получают больше баллов
        double score = 10 * (1 - Math.log(days + 1) / Math.log(maxDays + 1));
        return BigDecimal.valueOf(Math.max(0, Math.min(10, score)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Возраст: логарифм от дней с создания.
     * Старые эпики получают приоритет.
     * Формула: 10 * log(days + 1) / log(365)
     */
    private BigDecimal calculateAgeScore(JiraIssueEntity epic) {
        OffsetDateTime createdAt = epic.getJiraCreatedAt();
        if (createdAt == null) {
            createdAt = epic.getCreatedAt();
        }
        if (createdAt == null) {
            return BigDecimal.ZERO;
        }

        long days = ChronoUnit.DAYS.between(createdAt.toLocalDate(), LocalDate.now());
        if (days <= 0) {
            return BigDecimal.ZERO;
        }

        // Логарифмическая шкала: быстрый рост в начале, замедление к году
        double score = 10 * Math.log(days + 1) / Math.log(365);
        return BigDecimal.valueOf(Math.min(10, score))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Ручной boost: любое значение.
     * Позволяет вручную корректировать приоритет эпика.
     */
    private BigDecimal calculateManualBoostScore(JiraIssueEntity epic) {
        Integer boost = epic.getManualPriorityBoost();
        if (boost == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(boost);
    }

    /**
     * Получает общую оценку в днях (rough estimate или original estimate).
     */
    private BigDecimal getTotalEstimateDays(JiraIssueEntity epic) {
        // Сначала пробуем rough estimate
        BigDecimal sa = epic.getRoughEstimateSaDays();
        BigDecimal dev = epic.getRoughEstimateDevDays();
        BigDecimal qa = epic.getRoughEstimateQaDays();

        if (sa != null || dev != null || qa != null) {
            BigDecimal total = BigDecimal.ZERO;
            if (sa != null) total = total.add(sa);
            if (dev != null) total = total.add(dev);
            if (qa != null) total = total.add(qa);
            return total;
        }

        // Fallback на original estimate (в секундах -> дни, 8 часов = 1 день)
        Long estimateSeconds = epic.getOriginalEstimateSeconds();
        if (estimateSeconds != null && estimateSeconds > 0) {
            return BigDecimal.valueOf(estimateSeconds / 3600.0 / 8.0)
                    .setScale(1, RoundingMode.HALF_UP);
        }

        return null;
    }
}
