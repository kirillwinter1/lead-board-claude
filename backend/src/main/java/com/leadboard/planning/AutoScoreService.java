package com.leadboard.planning;

import com.leadboard.project.ProjectAlignmentService;
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
    private final ProjectAlignmentService projectAlignmentService;

    public AutoScoreService(AutoScoreCalculator calculator,
                            JiraIssueRepository issueRepository,
                            ProjectAlignmentService projectAlignmentService) {
        this.calculator = calculator;
        this.issueRepository = issueRepository;
        this.projectAlignmentService = projectAlignmentService;
    }

    /**
     * Пересчитывает AutoScore для всех эпиков.
     *
     * @return количество обновлённых эпиков
     */
    @Transactional
    public int recalculateAll() {
        List<JiraIssueEntity> epics = issueRepository.findByBoardCategory("EPIC");

        calculator.preloadRiceData(epics);
        calculator.preloadAlignmentData(projectAlignmentService.preloadAlignmentData(epics));
        try {
            OffsetDateTime now = OffsetDateTime.now();
            for (JiraIssueEntity epic : epics) {
                BigDecimal score = calculator.calculate(epic);
                epic.setAutoScore(score);
                epic.setAutoScoreCalculatedAt(now);
            }
            issueRepository.saveAll(epics);
        } finally {
            calculator.clearRiceData();
            calculator.clearAlignmentData();
        }

        log.info("Recalculated AutoScore for {} epics", epics.size());
        return epics.size();
    }

    /**
     * Пересчитывает AutoScore для эпиков команды.
     *
     * @param teamId ID команды
     * @return количество обновлённых эпиков
     */
    @Transactional
    public int recalculateForTeam(Long teamId) {
        List<JiraIssueEntity> epics = issueRepository.findByBoardCategoryAndTeamId("EPIC", teamId);

        calculator.preloadRiceData(epics);
        calculator.preloadAlignmentData(projectAlignmentService.preloadAlignmentData(epics));
        try {
            OffsetDateTime now = OffsetDateTime.now();
            for (JiraIssueEntity epic : epics) {
                BigDecimal score = calculator.calculate(epic);
                epic.setAutoScore(score);
                epic.setAutoScoreCalculatedAt(now);
            }
            issueRepository.saveAll(epics);
        } finally {
            calculator.clearRiceData();
            calculator.clearAlignmentData();
        }

        log.info("Recalculated AutoScore for {} epics of team {}", epics.size(), teamId);
        return epics.size();
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

        // Preload RICE and alignment data so single-epic breakdown matches batch calculation
        List<JiraIssueEntity> singleList = List.of(epic);
        calculator.preloadRiceData(singleList);
        calculator.preloadAlignmentData(projectAlignmentService.preloadAlignmentData(singleList));
        Map<String, BigDecimal> factors;
        try {
            factors = calculator.calculateFactors(epic);
        } finally {
            calculator.clearRiceData();
            calculator.clearAlignmentData();
        }

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
     * Получает эпики команды, отсортированные по AutoScore.
     *
     * @param teamId ID команды
     * @return список эпиков
     */
    public List<JiraIssueEntity> getEpicsByPriority(Long teamId) {
        return issueRepository.findByBoardCategoryAndTeamIdOrderByAutoScoreDesc("EPIC", teamId);
    }

    /**
     * Получает эпики с заданными статусами, отсортированные по AutoScore.
     *
     * @param teamId   ID команды
     * @param statuses список статусов
     * @return список эпиков
     */
    public List<JiraIssueEntity> getEpicsByPriorityAndStatus(Long teamId, List<String> statuses) {
        return issueRepository.findByBoardCategoryAndTeamIdAndStatusInOrderByAutoScoreDesc("EPIC", teamId, statuses);
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
