package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сервис управления AutoScore эпиков.
 */
@Service
public class AutoScoreService {

    private static final Logger log = LoggerFactory.getLogger(AutoScoreService.class);

    private final AutoScoreCalculator calculator;
    private final JiraIssueRepository issueRepository;

    public AutoScoreService(AutoScoreCalculator calculator, JiraIssueRepository issueRepository) {
        this.calculator = calculator;
        this.issueRepository = issueRepository;
    }

    // Поддержка локализованных типов
    private static final List<String> EPIC_TYPES = List.of("Epic", "Эпик");

    /**
     * Пересчитывает AutoScore для всех эпиков.
     *
     * @return количество обновлённых эпиков
     */
    @Transactional
    public int recalculateAll() {
        int count = 0;

        for (String epicType : EPIC_TYPES) {
            List<JiraIssueEntity> epics = issueRepository.findByIssueType(epicType);
            for (JiraIssueEntity epic : epics) {
                BigDecimal score = calculator.calculate(epic);
                epic.setAutoScore(score);
                epic.setAutoScoreCalculatedAt(OffsetDateTime.now());
                issueRepository.save(epic);
                count++;
            }
        }

        log.info("Recalculated AutoScore for {} epics", count);
        return count;
    }

    /**
     * Пересчитывает AutoScore для эпиков команды.
     *
     * @param teamId ID команды
     * @return количество обновлённых эпиков
     */
    @Transactional
    public int recalculateForTeam(Long teamId) {
        int count = 0;

        for (String epicType : EPIC_TYPES) {
            List<JiraIssueEntity> epics = issueRepository.findByIssueTypeAndTeamId(epicType, teamId);
            for (JiraIssueEntity epic : epics) {
                BigDecimal score = calculator.calculate(epic);
                epic.setAutoScore(score);
                epic.setAutoScoreCalculatedAt(OffsetDateTime.now());
                issueRepository.save(epic);
                count++;
            }
        }

        log.info("Recalculated AutoScore for {} epics of team {}", count, teamId);
        return count;
    }

    /**
     * Пересчитывает AutoScore для одного эпика.
     *
     * @param epicKey ключ эпика
     * @return новый AutoScore или null если эпик не найден
     */
    @Transactional
    public BigDecimal recalculateForEpic(String epicKey) {
        Optional<JiraIssueEntity> epicOpt = issueRepository.findByIssueKey(epicKey);
        if (epicOpt.isEmpty()) {
            log.warn("Epic not found: {}", epicKey);
            return null;
        }

        JiraIssueEntity epic = epicOpt.get();
        BigDecimal score = calculator.calculate(epic);
        epic.setAutoScore(score);
        epic.setAutoScoreCalculatedAt(OffsetDateTime.now());
        issueRepository.save(epic);

        log.debug("Recalculated AutoScore for {}: {}", epicKey, score);
        return score;
    }

    /**
     * Получает детализацию AutoScore для эпика.
     *
     * @param epicKey ключ эпика
     * @return карта фактор -> значение или null если эпик не найден
     */
    public AutoScoreDetails getScoreDetails(String epicKey) {
        Optional<JiraIssueEntity> epicOpt = issueRepository.findByIssueKey(epicKey);
        if (epicOpt.isEmpty()) {
            return null;
        }

        JiraIssueEntity epic = epicOpt.get();
        Map<String, BigDecimal> factors = calculator.calculateFactors(epic);
        BigDecimal total = factors.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AutoScoreDetails(
                epicKey,
                total,
                epic.getAutoScoreCalculatedAt(),
                factors
        );
    }

    /**
     * Обновляет ручной boost приоритета.
     *
     * @param epicKey ключ эпика
     * @param boost   новое значение (0-5)
     * @return новый AutoScore или null если эпик не найден
     */
    @Transactional
    public BigDecimal updateManualBoost(String epicKey, int boost) {
        Optional<JiraIssueEntity> epicOpt = issueRepository.findByIssueKey(epicKey);
        if (epicOpt.isEmpty()) {
            log.warn("Epic not found: {}", epicKey);
            return null;
        }

        // Валидация
        int validBoost = Math.max(0, Math.min(5, boost));

        JiraIssueEntity epic = epicOpt.get();
        epic.setManualPriorityBoost(validBoost);

        // Пересчитываем AutoScore
        BigDecimal score = calculator.calculate(epic);
        epic.setAutoScore(score);
        epic.setAutoScoreCalculatedAt(OffsetDateTime.now());
        issueRepository.save(epic);

        log.info("Updated manual boost for {}: {} -> AutoScore: {}", epicKey, validBoost, score);
        return score;
    }

    /**
     * Получает эпики команды, отсортированные по AutoScore.
     *
     * @param teamId ID команды
     * @return список эпиков
     */
    public List<JiraIssueEntity> getEpicsByPriority(Long teamId) {
        return issueRepository.findByIssueTypeInAndTeamIdOrderByAutoScoreDesc(EPIC_TYPES, teamId);
    }

    /**
     * Получает эпики с заданными статусами, отсортированные по AutoScore.
     *
     * @param teamId   ID команды
     * @param statuses список статусов
     * @return список эпиков
     */
    public List<JiraIssueEntity> getEpicsByPriorityAndStatus(Long teamId, List<String> statuses) {
        return issueRepository.findByIssueTypeInAndTeamIdAndStatusInOrderByAutoScoreDesc(EPIC_TYPES, teamId, statuses);
    }

    /**
     * DTO для детализации AutoScore.
     */
    public record AutoScoreDetails(
            String epicKey,
            BigDecimal totalScore,
            OffsetDateTime calculatedAt,
            Map<String, BigDecimal> factors
    ) {}
}
