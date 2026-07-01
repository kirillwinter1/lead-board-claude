package com.leadboard.insight;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Движок инсайтов (F80): 4 линзы готовности команды.
 *
 * <p>Цифры детерминированы; LLM (через MCP-инструмент {@code team_readiness_briefing})
 * формулирует их человечно. Приоритет линз откалиброван на реальных данных:
 * планирование → загрузка → качество данных → поток.</p>
 */
@Service
public class InsightEngine {

    private static final Pattern QUARTER = Pattern.compile("^\\d{4}Q[1-4]$");

    /** Анти-шум: не вываливать более этого числа ключей в одну линзу. */
    private static final int MAX_KEYS = 15;

    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;

    public InsightEngine(JiraIssueRepository issueRepository, WorkflowConfigService workflowConfigService) {
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
    }

    public TeamReadiness briefing(Long teamId) {
        List<JiraIssueEntity> stories = issueRepository.findActiveStoriesForReadiness(teamId).stream()
                .filter(s -> !isDone(s))
                .toList();
        return new TeamReadiness(
                teamId,
                planningLens(stories),
                loadLens(stories),
                dataQualityLens(stories),
                flowLens(stories)
        );
    }

    private TeamReadiness.Lens planningLens(List<JiraIssueEntity> stories) {
        List<String> noQuarter = new ArrayList<>();
        for (JiraIssueEntity s : stories) {
            if (!hasQuarter(s)) {
                noQuarter.add(s.getIssueKey());
            }
        }
        int total = stories.size();
        double share = total == 0 ? 0 : (double) noQuarter.size() / total;
        String level = share >= 0.5 ? "RED" : share >= 0.2 ? "YELLOW" : "GREEN";
        String headline = noQuarter.size() + " из " + total + " историй без квартальной метки";
        return new TeamReadiness.Lens(level, headline,
                List.of("Доля без квартала: " + Math.round(share * 100) + "%",
                        "Без разметки по кварталам нельзя планировать загрузку на период"),
                cap(noQuarter));
    }

    private TeamReadiness.Lens loadLens(List<JiraIssueEntity> stories) {
        // Фаза 1: эвристика-заглушка с честной оговоркой о достоверности worklog.
        // Полная реализация (capacity F55 vs назначенный объём + worklog по людям) — следующая фаза.
        long assigned = stories.stream().filter(s -> s.getAssigneeDisplayName() != null).count();
        long unassigned = stories.size() - assigned;
        String headline = unassigned + " активных историй без исполнителя";
        return new TeamReadiness.Lens("YELLOW", headline,
                List.of("Параллельную загрузку по людям можно считать только при заполненных исполнителях",
                        "Если worklog списан на одного человека или превышает физический максимум — данные недостоверны"),
                List.of());
    }

    private TeamReadiness.Lens dataQualityLens(List<JiraIssueEntity> stories) {
        List<String> noEstimate = new ArrayList<>();
        int noAssignee = 0;
        for (JiraIssueEntity s : stories) {
            if (s.getRoughEstimates() == null || s.getRoughEstimates().isEmpty()) {
                noEstimate.add(s.getIssueKey());
            }
            if (s.getAssigneeDisplayName() == null) {
                noAssignee++;
            }
        }
        int total = stories.size();
        String level = noEstimate.size() > total / 2 ? "RED" : noEstimate.isEmpty() ? "GREEN" : "YELLOW";
        String headline = noEstimate.size() + " историй без оценки, " + noAssignee + " без исполнителя";
        return new TeamReadiness.Lens(level, headline,
                List.of("Без оценки доверять плану нельзя",
                        "Без исполнителя нельзя считать загрузку"),
                cap(noEstimate));
    }

    private TeamReadiness.Lens flowLens(List<JiraIssueEntity> stories) {
        // Фаза 1: самая длинная очередь по статусу как кандидат на бутылочное горло.
        // Полная реализация (stuck epics из F79 + at-risk дедлайны) — следующая фаза.
        Map<String, Long> byStatus = new HashMap<>();
        for (JiraIssueEntity s : stories) {
            byStatus.merge(s.getStatus() == null ? "—" : s.getStatus(), 1L, Long::sum);
        }
        String bottleneck = byStatus.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .orElse("нет данных");
        return new TeamReadiness.Lens("YELLOW",
                "Самая длинная очередь: " + bottleneck,
                List.of("Стадия с наибольшим числом историй — кандидат на бутылочное горло"),
                List.of());
    }

    private boolean isDone(JiraIssueEntity s) {
        try {
            return workflowConfigService.isDone(s.getStatus(), s.getIssueType());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasQuarter(JiraIssueEntity s) {
        if (s.getLabels() == null) {
            return false;
        }
        for (String l : s.getLabels()) {
            if (l != null && QUARTER.matcher(l).matches()) {
                return true;
            }
        }
        return false;
    }

    private List<String> cap(List<String> keys) {
        return keys.size() <= MAX_KEYS ? keys : keys.subList(0, MAX_KEYS);
    }
}
