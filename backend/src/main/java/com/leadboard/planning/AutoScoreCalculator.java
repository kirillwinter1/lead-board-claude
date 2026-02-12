package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Расчёт AutoScore — автоматического приоритета эпика для планирования.
 *
 * Принцип: "Закончить начатое" — эпики на поздних стадиях workflow имеют высший приоритет.
 *
 * Факторы и их веса:
 * - Статус (-5 to +30): позиция в Epic Workflow
 *   - ACCEPTANCE/E2E TESTING = +30 (почти готово)
 *   - DEVELOPING = +25 (активная разработка)
 *   - ЗАПЛАНИРОВАНО = +15 (готов к старту)
 *   - ROUGH ESTIMATE = +10 (оценивается)
 *   - REQUIREMENTS = +5 (пишутся БТ)
 *   - НОВОЕ = -5 (не начат)
 * - Priority (20): Jira приоритет (Highest=20, High=15, Medium=10, Low=5)
 * - Due date (25): экспоненциальный рост к дедлайну
 * - Прогресс (10): процент выполнения (logged/estimate)
 * - Размер (5): инверсия от estimate (меньше = выше, без оценки = -5)
 * - Возраст (5): логарифм от дней с создания
 *
 * Порядок эпиков определяется полем manual_order (drag & drop).
 * AutoScore используется только для рекомендаций.
 */
@Service
public class AutoScoreCalculator {

    private static final Logger log = LoggerFactory.getLogger(AutoScoreCalculator.class);

    private final JiraIssueRepository issueRepository;

    public AutoScoreCalculator(JiraIssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    // Веса факторов (обновлены 2026-01-26)
    private static final BigDecimal WEIGHT_PROGRESS = new BigDecimal("10");
    private static final BigDecimal WEIGHT_DUE_DATE = new BigDecimal("25");
    private static final BigDecimal WEIGHT_PRIORITY = new BigDecimal("20");
    private static final BigDecimal WEIGHT_SIZE = new BigDecimal("5");
    private static final BigDecimal WEIGHT_AGE = new BigDecimal("5");

    // Баллы за статус в Epic Workflow (чем дальше по workflow — тем выше)
    private static final int STATUS_SCORE_ACCEPTANCE = 30;    // Почти готово
    private static final int STATUS_SCORE_E2E_TESTING = 30;   // Почти готово
    private static final int STATUS_SCORE_DEVELOPING = 25;    // Активная разработка
    private static final int STATUS_SCORE_PLANNED = 15;       // Готов к старту
    private static final int STATUS_SCORE_ROUGH_ESTIMATE = 10; // Оценивается
    private static final int STATUS_SCORE_REQUIREMENTS = 5;   // Пишутся БТ
    private static final int STATUS_SCORE_NEW = -5;           // Не начат

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

        return factors;
    }

    /**
     * Статус: баллы зависят от позиции в Epic Workflow.
     * Чем дальше по workflow — тем выше приоритет (закончить начатое).
     */
    private BigDecimal calculateStatusScore(JiraIssueEntity epic) {
        String status = epic.getStatus();
        if (status == null) {
            return BigDecimal.ZERO;
        }

        String statusLower = status.toLowerCase();

        // Финальные стадии — максимальный приоритет
        if (statusLower.contains("acceptance") || statusLower.contains("приёмка") || statusLower.contains("приемка")) {
            return BigDecimal.valueOf(STATUS_SCORE_ACCEPTANCE);
        }
        if (statusLower.contains("e2e") || statusLower.contains("end-to-end") || statusLower.contains("e2e testing")) {
            return BigDecimal.valueOf(STATUS_SCORE_E2E_TESTING);
        }

        // Активная разработка
        if (statusLower.contains("developing") || statusLower.contains("development") ||
            statusLower.contains("in progress") || statusLower.contains("в работе") ||
            statusLower.contains("в разработке")) {
            return BigDecimal.valueOf(STATUS_SCORE_DEVELOPING);
        }

        // Готов к старту
        if (statusLower.contains("запланировано") || statusLower.contains("planned") ||
            statusLower.contains("ready")) {
            return BigDecimal.valueOf(STATUS_SCORE_PLANNED);
        }

        // Оценивается
        if (statusLower.contains("rough estimate") || statusLower.contains("estimation") ||
            statusLower.contains("оценка") || statusLower.contains("estimate")) {
            return BigDecimal.valueOf(STATUS_SCORE_ROUGH_ESTIMATE);
        }

        // Пишутся БТ
        if (statusLower.contains("requirements") || statusLower.contains("требования") ||
            statusLower.contains("analysis") || statusLower.contains("аналитика")) {
            return BigDecimal.valueOf(STATUS_SCORE_REQUIREMENTS);
        }

        // Не начат
        if (statusLower.contains("новое") || statusLower.contains("new") ||
            statusLower.contains("backlog") || statusLower.contains("to do") ||
            statusLower.contains("open")) {
            return BigDecimal.valueOf(STATUS_SCORE_NEW);
        }

        // Неизвестный статус — нейтральный балл
        return BigDecimal.ZERO;
    }

    /**
     * Прогресс: (logged / estimate) * 10
     * Агрегирует данные из subtask'ов (Epic → Story → Subtask).
     * Чем ближе к завершению, тем выше приоритет (быстрее закрыть).
     */
    private BigDecimal calculateProgressScore(JiraIssueEntity epic) {
        // Find stories under this epic
        List<JiraIssueEntity> stories = issueRepository.findByParentKey(epic.getIssueKey());
        if (stories.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Find subtasks under all stories
        List<String> storyKeys = stories.stream().map(JiraIssueEntity::getIssueKey).toList();
        List<JiraIssueEntity> subtasks = issueRepository.findByParentKeyIn(storyKeys);

        long totalEstimate = 0;
        long totalLogged = 0;

        for (JiraIssueEntity subtask : subtasks) {
            totalEstimate += subtask.getEffectiveEstimateSeconds();
            totalLogged += subtask.getTimeSpentSeconds() != null ? subtask.getTimeSpentSeconds() : 0;
        }

        if (totalEstimate == 0) {
            return BigDecimal.ZERO;
        }

        double progress = Math.min(1.0, (double) totalLogged / totalEstimate);

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
     * Priority: Highest=20, High=15, Medium=10, Low=5, Lowest=0
     * Увеличен вес Jira Priority как осознанного бизнес-решения.
     */
    private BigDecimal calculatePriorityScore(JiraIssueEntity epic) {
        String priority = epic.getPriority();
        if (priority == null) {
            return BigDecimal.ZERO;
        }

        return switch (priority.toLowerCase()) {
            case "highest", "blocker", "критический" -> WEIGHT_PRIORITY; // 20
            case "high", "critical", "высокий" -> new BigDecimal("15");
            case "medium", "major", "средний" -> new BigDecimal("10");
            case "low", "minor", "низкий" -> new BigDecimal("5");
            case "lowest", "trivial", "минимальный" -> BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Размер: инверсия от estimate (меньше эпик = выше приоритет, быстрее закрыть).
     * Формула: 5 * (1 - log(estimate_days) / log(max_days))
     * где max_days = 90 (для нормализации)
     *
     * Без оценки = -5 (штраф за отсутствие оценки, а не бонус!)
     */
    private BigDecimal calculateSizeScore(JiraIssueEntity epic) {
        // Используем rough estimate если есть, иначе original estimate
        BigDecimal totalDays = getTotalEstimateDays(epic);

        if (totalDays == null || totalDays.compareTo(BigDecimal.ZERO) <= 0) {
            // Нет оценки — штраф! Эпик без оценки должен быть внизу списка.
            return new BigDecimal("-5");
        }

        double days = totalDays.doubleValue();
        double maxDays = 90.0;

        if (days >= maxDays) {
            return BigDecimal.ZERO;
        }

        // Логарифмическая шкала: маленькие эпики получают больше баллов (max 5)
        double score = 5 * (1 - Math.log(days + 1) / Math.log(maxDays + 1));
        return BigDecimal.valueOf(Math.max(0, Math.min(5, score)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Возраст: логарифм от дней с создания.
     * Старые эпики получают небольшой бонус.
     * Формула: 5 * log(days + 1) / log(365)
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

        // Логарифмическая шкала: быстрый рост в начале, замедление к году (max 5)
        double score = 5 * Math.log(days + 1) / Math.log(365);
        return BigDecimal.valueOf(Math.min(5, score))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Получает общую оценку в днях.
     * Приоритет: rough estimate → агрегация из subtasks → original estimate на эпике.
     */
    private BigDecimal getTotalEstimateDays(JiraIssueEntity epic) {
        // 1. Rough estimate на эпике
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

        // 2. Агрегация original estimate из subtasks (Epic → Story → Subtask)
        List<JiraIssueEntity> stories = issueRepository.findByParentKey(epic.getIssueKey());
        if (!stories.isEmpty()) {
            List<String> storyKeys = stories.stream().map(JiraIssueEntity::getIssueKey).toList();
            List<JiraIssueEntity> subtasks = issueRepository.findByParentKeyIn(storyKeys);

            long totalEstimateSeconds = 0;
            for (JiraIssueEntity subtask : subtasks) {
                totalEstimateSeconds += subtask.getEffectiveEstimateSeconds();
            }

            if (totalEstimateSeconds > 0) {
                return BigDecimal.valueOf(totalEstimateSeconds / 3600.0 / 8.0)
                        .setScale(1, RoundingMode.HALF_UP);
            }
        }

        return null;
    }
}
