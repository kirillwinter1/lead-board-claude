package com.leadboard.competency;

import com.leadboard.team.TeamMemberEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CompetencyScoreCalculator {

    private final MemberCompetencyRepository repository;

    public CompetencyScoreCalculator(MemberCompetencyRepository repository) {
        this.repository = repository;
    }

    /**
     * Pre-load all competencies for a list of team members.
     * Returns: Map<jiraAccountId, Map<componentName, level>>
     */
    public Map<String, Map<String, Integer>> loadForMembers(List<TeamMemberEntity> members) {
        List<Long> memberIds = members.stream().map(TeamMemberEntity::getId).toList();
        if (memberIds.isEmpty()) return Map.of();

        // Build accountId lookup: memberId -> jiraAccountId
        Map<Long, String> idToAccount = new HashMap<>();
        for (TeamMemberEntity m : members) {
            idToAccount.put(m.getId(), m.getJiraAccountId());
        }

        List<MemberCompetencyEntity> allCompetencies = repository.findByTeamMemberIdIn(memberIds);

        Map<String, Map<String, Integer>> result = new HashMap<>();
        for (MemberCompetencyEntity comp : allCompetencies) {
            String accountId = idToAccount.get(comp.getTeamMember().getId());
            if (accountId == null) continue;
            result.computeIfAbsent(accountId, k -> new HashMap<>())
                    .put(comp.getComponentName(), comp.getLevel());
        }
        return result;
    }

    /**
     * Calculate average competency score for a member given task components.
     * Returns 3.0 (neutral) if no matching components or empty lists.
     */
    public double calculateScore(Map<String, Integer> memberCompetencies, List<String> taskComponents) {
        if (memberCompetencies == null || memberCompetencies.isEmpty()
                || taskComponents == null || taskComponents.isEmpty()) {
            return 3.0;
        }

        double sum = 0;
        int count = 0;
        for (String component : taskComponents) {
            Integer level = memberCompetencies.get(component);
            if (level != null) {
                sum += level;
                count++;
            }
        }

        if (count == 0) return 3.0;
        return sum / count;
    }

    /**
     * Piecewise linear interpolation:
     *   score 1 -> 1.6 (novice takes 60% longer)
     *   score 3 -> 1.0 (neutral, no effect)
     *   score 5 -> 0.7 (expert finishes 30% faster)
     *
     * Factor < 1.0 means expert finishes faster (fewer hours needed).
     * Factor > 1.0 means novice takes longer.
     * Score 3.0 (default) = factor 1.0 = no change to hours.
     */
    public static BigDecimal getCompetencyFactor(double score) {
        double factor;
        if (score <= 3.0) {
            // 1->1.6, 3->1.0: slope = -0.3 per point
            factor = 1.0 + (3.0 - score) * 0.3;
        } else {
            // 3->1.0, 5->0.7: slope = -0.15 per point
            factor = 1.0 - (score - 3.0) * 0.15;
        }
        return BigDecimal.valueOf(factor).setScale(3, RoundingMode.HALF_UP);
    }
}
