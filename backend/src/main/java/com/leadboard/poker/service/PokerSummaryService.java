package com.leadboard.poker.service;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.poker.dto.SessionSummaryResponse;
import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.entity.PokerStoryEntity.StoryStatus;
import com.leadboard.poker.repository.PokerSessionRepository;
import com.leadboard.poker.repository.PokerStoryRepository;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Builds the session summary (F23 rework): final poker estimates per story and a
 * rough-vs-poker comparison by role.
 *
 * Units: poker final estimates are hours; the comparison is expressed in days
 * (1 d = 8 h) for a like-for-like comparison against the epic's rough estimate
 * (stored in days). Planning error = Σ|pokerDays_role − roughDays_role| — both
 * under- and over-estimates contribute.
 */
@Service
public class PokerSummaryService {

    private static final double HOURS_PER_DAY = 8.0;

    private final PokerSessionRepository sessionRepository;
    private final PokerStoryRepository storyRepository;
    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;

    public PokerSummaryService(PokerSessionRepository sessionRepository,
                               PokerStoryRepository storyRepository,
                               JiraIssueRepository issueRepository,
                               WorkflowConfigService workflowConfigService) {
        this.sessionRepository = sessionRepository;
        this.storyRepository = storyRepository;
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
    }

    @Transactional(readOnly = true)
    public SessionSummaryResponse buildSummary(Long sessionId) {
        PokerSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        List<PokerStoryEntity> stories = storyRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);

        List<SessionSummaryResponse.StoryEstimate> storyEstimates = new ArrayList<>();
        Map<String, Integer> pokerHoursByRole = new LinkedHashMap<>();

        for (PokerStoryEntity story : stories) {
            Map<String, Integer> finals = story.getFinalEstimates();
            if (story.getStatus() != StoryStatus.COMPLETED || finals == null || finals.isEmpty()) {
                continue;
            }
            int totalHours = 0;
            for (Map.Entry<String, Integer> e : finals.entrySet()) {
                int hours = e.getValue() != null ? e.getValue() : 0;
                if (hours <= 0) continue;
                totalHours += hours;
                pokerHoursByRole.merge(e.getKey(), hours, Integer::sum);
            }
            storyEstimates.add(new SessionSummaryResponse.StoryEstimate(
                    story.getStoryKey(), story.getTitle(), finals, totalHours));
        }

        // Rough estimates (days) from the epic itself.
        Map<String, BigDecimal> rough = issueRepository.findByIssueKey(session.getEpicKey())
                .map(JiraIssueEntity::getRoughEstimates)
                .orElse(Map.of());

        // Stable role order: pipeline order first, then any extra roles present in the data.
        LinkedHashSet<String> roleOrder = new LinkedHashSet<>(workflowConfigService.getRoleCodesInPipelineOrder());
        if (rough != null) roleOrder.addAll(rough.keySet());
        roleOrder.addAll(pokerHoursByRole.keySet());

        List<SessionSummaryResponse.RoleComparison> comparison = new ArrayList<>();
        double roughTotal = 0;
        double pokerTotal = 0;
        double errorDays = 0;

        for (String role : roleOrder) {
            double roughDays = roughDaysFor(rough, role);
            double pokerDays = (pokerHoursByRole.getOrDefault(role, 0)) / HOURS_PER_DAY;
            if (roughDays == 0 && pokerDays == 0) {
                continue; // role irrelevant to this comparison
            }
            double delta = pokerDays - roughDays;
            comparison.add(new SessionSummaryResponse.RoleComparison(role, roughDays, pokerDays, delta));
            roughTotal += roughDays;
            pokerTotal += pokerDays;
            errorDays += Math.abs(delta);
        }

        double errorPercent = roughTotal > 0 ? (errorDays / roughTotal) * 100.0 : 0.0;

        return new SessionSummaryResponse(
                sessionId,
                session.getEpicKey(),
                storyEstimates,
                pokerTotal,
                comparison,
                roughTotal,
                pokerTotal,
                errorDays,
                errorPercent);
    }

    private static double roughDaysFor(Map<String, BigDecimal> rough, String role) {
        if (rough == null) return 0;
        BigDecimal days = rough.get(role);
        if (days == null) {
            // rough estimate role codes are stored upper-cased; be lenient on case
            for (Map.Entry<String, BigDecimal> e : rough.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(role)) {
                    days = e.getValue();
                    break;
                }
            }
        }
        return days != null ? days.doubleValue() : 0;
    }
}
