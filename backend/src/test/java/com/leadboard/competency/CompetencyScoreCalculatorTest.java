package com.leadboard.competency;

import com.leadboard.team.TeamMemberEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetencyScoreCalculatorTest {

    @Mock
    private MemberCompetencyRepository repository;

    private CompetencyScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CompetencyScoreCalculator(repository);
    }

    // ==================== calculateScore tests ====================

    @Test
    void calculateScore_noCompetencies_returnsDefault() {
        double score = calculator.calculateScore(Map.of(), List.of("Frontend"));
        assertEquals(3.0, score);
    }

    @Test
    void calculateScore_noTaskComponents_returnsDefault() {
        double score = calculator.calculateScore(Map.of("Frontend", 5), List.of());
        assertEquals(3.0, score);
    }

    @Test
    void calculateScore_nullCompetencies_returnsDefault() {
        double score = calculator.calculateScore(null, List.of("Frontend"));
        assertEquals(3.0, score);
    }

    @Test
    void calculateScore_nullComponents_returnsDefault() {
        double score = calculator.calculateScore(Map.of("Frontend", 5), null);
        assertEquals(3.0, score);
    }

    @Test
    void calculateScore_singleMatch_returnsLevel() {
        double score = calculator.calculateScore(Map.of("Frontend", 5), List.of("Frontend"));
        assertEquals(5.0, score);
    }

    @Test
    void calculateScore_multipleMatches_returnsAverage() {
        Map<String, Integer> comp = Map.of("Frontend", 5, "Backend", 3);
        double score = calculator.calculateScore(comp, List.of("Frontend", "Backend"));
        assertEquals(4.0, score);
    }

    @Test
    void calculateScore_partialMatch_averagesOnlyMatches() {
        Map<String, Integer> comp = Map.of("Frontend", 5);
        double score = calculator.calculateScore(comp, List.of("Frontend", "Backend"));
        assertEquals(5.0, score); // Only Frontend matches
    }

    @Test
    void calculateScore_noMatch_returnsDefault() {
        Map<String, Integer> comp = Map.of("Frontend", 5);
        double score = calculator.calculateScore(comp, List.of("Backend", "DevOps"));
        assertEquals(3.0, score);
    }

    // ==================== getCompetencyFactor tests ====================

    @Test
    void factor_novice_score1_returns1_6() {
        BigDecimal factor = CompetencyScoreCalculator.getCompetencyFactor(1.0);
        assertEquals(0, new BigDecimal("1.600").compareTo(factor));
    }

    @Test
    void factor_competent_score3_returns1_0() {
        BigDecimal factor = CompetencyScoreCalculator.getCompetencyFactor(3.0);
        assertEquals(0, new BigDecimal("1.000").compareTo(factor));
    }

    @Test
    void factor_expert_score5_returns0_7() {
        BigDecimal factor = CompetencyScoreCalculator.getCompetencyFactor(5.0);
        assertEquals(0, new BigDecimal("0.700").compareTo(factor));
    }

    @Test
    void factor_beginner_score2_returns1_3() {
        BigDecimal factor = CompetencyScoreCalculator.getCompetencyFactor(2.0);
        assertEquals(0, new BigDecimal("1.300").compareTo(factor));
    }

    @Test
    void factor_proficient_score4_returns0_85() {
        BigDecimal factor = CompetencyScoreCalculator.getCompetencyFactor(4.0);
        assertEquals(0, new BigDecimal("0.850").compareTo(factor));
    }

    @Test
    void factor_default3_returns_neutral() {
        // Default score 3.0 should result in factor = 1.0 (neutral, no change)
        BigDecimal factor = CompetencyScoreCalculator.getCompetencyFactor(3.0);
        assertEquals(0, BigDecimal.ONE.compareTo(factor));
    }

    @Test
    void factor_expert_reduces_hours() {
        BigDecimal factor = CompetencyScoreCalculator.getCompetencyFactor(5.0);
        // Expert: 10h * 0.7 = 7h
        BigDecimal adjusted = new BigDecimal("10").multiply(factor);
        assertTrue(adjusted.compareTo(new BigDecimal("10")) < 0);
    }

    @Test
    void factor_novice_increases_hours() {
        BigDecimal factor = CompetencyScoreCalculator.getCompetencyFactor(1.0);
        // Novice: 10h * 1.6 = 16h
        BigDecimal adjusted = new BigDecimal("10").multiply(factor);
        assertTrue(adjusted.compareTo(new BigDecimal("10")) > 0);
    }

    // ==================== loadForMembers tests ====================

    @Test
    void loadForMembers_emptyList_returnsEmpty() {
        Map<String, Map<String, Integer>> result = calculator.loadForMembers(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void loadForMembers_noCompetencies_returnsEmpty() {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setId(1L);
        member.setJiraAccountId("acc-1");
        when(repository.findByTeamMemberIdIn(List.of(1L))).thenReturn(List.of());

        Map<String, Map<String, Integer>> result = calculator.loadForMembers(List.of(member));
        assertTrue(result.isEmpty());
    }

    @Test
    void loadForMembers_withCompetencies_mapsCorrectly() {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setId(1L);
        member.setJiraAccountId("acc-1");

        MemberCompetencyEntity comp = new MemberCompetencyEntity();
        comp.setTeamMember(member);
        comp.setComponentName("Frontend");
        comp.setLevel(4);

        when(repository.findByTeamMemberIdIn(List.of(1L))).thenReturn(List.of(comp));

        Map<String, Map<String, Integer>> result = calculator.loadForMembers(List.of(member));
        assertEquals(1, result.size());
        assertEquals(4, result.get("acc-1").get("Frontend"));
    }
}
