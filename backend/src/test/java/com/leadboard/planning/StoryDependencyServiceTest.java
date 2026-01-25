package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StoryDependencyServiceTest {

    private StoryDependencyService service;

    @BeforeEach
    void setUp() {
        service = new StoryDependencyService();
    }

    // ==================== Topological Sort Tests ====================

    @Test
    void topologicalSortWithNoDependenciesSortsByScore() {
        JiraIssueEntity story1 = createStory("PROJ-1", 10.0);
        JiraIssueEntity story2 = createStory("PROJ-2", 20.0);
        JiraIssueEntity story3 = createStory("PROJ-3", 15.0);

        Map<String, Double> scores = Map.of(
            "PROJ-1", 10.0,
            "PROJ-2", 20.0,
            "PROJ-3", 15.0
        );

        List<JiraIssueEntity> sorted = service.topologicalSort(List.of(story1, story2, story3), scores);

        // Should be sorted by score descending: PROJ-2 (20), PROJ-3 (15), PROJ-1 (10)
        assertEquals("PROJ-2", sorted.get(0).getIssueKey());
        assertEquals("PROJ-3", sorted.get(1).getIssueKey());
        assertEquals("PROJ-1", sorted.get(2).getIssueKey());
    }

    @Test
    void topologicalSortRespectsSimpleBlockingDependency() {
        JiraIssueEntity story1 = createStory("PROJ-1", 100.0);
        story1.setBlocks(List.of("PROJ-2")); // PROJ-1 blocks PROJ-2

        JiraIssueEntity story2 = createStory("PROJ-2", 200.0);
        story2.setIsBlockedBy(List.of("PROJ-1")); // PROJ-2 is blocked by PROJ-1

        Map<String, Double> scores = Map.of(
            "PROJ-1", 100.0,
            "PROJ-2", 200.0
        );

        List<JiraIssueEntity> sorted = service.topologicalSort(List.of(story1, story2), scores);

        // PROJ-1 must come before PROJ-2, even though PROJ-2 has higher score
        assertEquals("PROJ-1", sorted.get(0).getIssueKey());
        assertEquals("PROJ-2", sorted.get(1).getIssueKey());
    }

    @Test
    void topologicalSortHandlesChainDependencies() {
        // Chain: PROJ-1 -> PROJ-2 -> PROJ-3
        JiraIssueEntity story1 = createStory("PROJ-1", 10.0);
        story1.setBlocks(List.of("PROJ-2"));

        JiraIssueEntity story2 = createStory("PROJ-2", 20.0);
        story2.setIsBlockedBy(List.of("PROJ-1"));
        story2.setBlocks(List.of("PROJ-3"));

        JiraIssueEntity story3 = createStory("PROJ-3", 30.0);
        story3.setIsBlockedBy(List.of("PROJ-2"));

        Map<String, Double> scores = Map.of(
            "PROJ-1", 10.0,
            "PROJ-2", 20.0,
            "PROJ-3", 30.0
        );

        List<JiraIssueEntity> sorted = service.topologicalSort(List.of(story1, story2, story3), scores);

        // Must maintain dependency order: PROJ-1, PROJ-2, PROJ-3
        assertEquals("PROJ-1", sorted.get(0).getIssueKey());
        assertEquals("PROJ-2", sorted.get(1).getIssueKey());
        assertEquals("PROJ-3", sorted.get(2).getIssueKey());
    }

    @Test
    void topologicalSortHandlesDiamondDependencies() {
        // Diamond: PROJ-1 blocks both PROJ-2 and PROJ-3, both block PROJ-4
        JiraIssueEntity story1 = createStory("PROJ-1", 10.0);
        story1.setBlocks(List.of("PROJ-2", "PROJ-3"));

        JiraIssueEntity story2 = createStory("PROJ-2", 30.0);
        story2.setIsBlockedBy(List.of("PROJ-1"));
        story2.setBlocks(List.of("PROJ-4"));

        JiraIssueEntity story3 = createStory("PROJ-3", 20.0);
        story3.setIsBlockedBy(List.of("PROJ-1"));
        story3.setBlocks(List.of("PROJ-4"));

        JiraIssueEntity story4 = createStory("PROJ-4", 40.0);
        story4.setIsBlockedBy(List.of("PROJ-2", "PROJ-3"));

        Map<String, Double> scores = Map.of(
            "PROJ-1", 10.0,
            "PROJ-2", 30.0,
            "PROJ-3", 20.0,
            "PROJ-4", 40.0
        );

        List<JiraIssueEntity> sorted = service.topologicalSort(List.of(story1, story2, story3, story4), scores);

        // PROJ-1 must come first
        assertEquals("PROJ-1", sorted.get(0).getIssueKey());

        // PROJ-2 and PROJ-3 can be in any order, but both must come before PROJ-4
        List<String> middleKeys = List.of(sorted.get(1).getIssueKey(), sorted.get(2).getIssueKey());
        assertTrue(middleKeys.contains("PROJ-2"));
        assertTrue(middleKeys.contains("PROJ-3"));

        // PROJ-4 must come last
        assertEquals("PROJ-4", sorted.get(3).getIssueKey());
    }

    @Test
    void topologicalSortWithinSameLayerSortsByScore() {
        // Two independent stories with different scores
        JiraIssueEntity story1 = createStory("PROJ-1", 50.0);
        JiraIssueEntity story2 = createStory("PROJ-2", 100.0);

        Map<String, Double> scores = Map.of(
            "PROJ-1", 50.0,
            "PROJ-2", 100.0
        );

        List<JiraIssueEntity> sorted = service.topologicalSort(List.of(story1, story2), scores);

        // Higher score should come first
        assertEquals("PROJ-2", sorted.get(0).getIssueKey());
        assertEquals("PROJ-1", sorted.get(1).getIssueKey());
    }

    @Test
    void topologicalSortIgnoresNonExistentDependencies() {
        JiraIssueEntity story1 = createStory("PROJ-1", 10.0);
        story1.setBlocks(List.of("PROJ-999")); // Non-existent story

        JiraIssueEntity story2 = createStory("PROJ-2", 20.0);

        Map<String, Double> scores = Map.of(
            "PROJ-1", 10.0,
            "PROJ-2", 20.0
        );

        List<JiraIssueEntity> sorted = service.topologicalSort(List.of(story1, story2), scores);

        // Should not crash, just sort by score
        assertEquals(2, sorted.size());
        assertEquals("PROJ-2", sorted.get(0).getIssueKey());
    }

    @Test
    void topologicalSortHandlesEmptyList() {
        List<JiraIssueEntity> sorted = service.topologicalSort(List.of(), Map.of());

        assertTrue(sorted.isEmpty());
    }

    // ==================== Can Start Tests ====================

    @Test
    void canStartReturnsTrueWhenNoDependencies() {
        JiraIssueEntity story = createStory("PROJ-1", 10.0);

        boolean canStart = service.canStart(story, Set.of());

        assertTrue(canStart);
    }

    @Test
    void canStartReturnsTrueWhenBlockersAreCompleted() {
        JiraIssueEntity story = createStory("PROJ-1", 10.0);
        story.setIsBlockedBy(List.of("PROJ-2", "PROJ-3"));

        Set<String> completedStories = Set.of("PROJ-2", "PROJ-3");

        boolean canStart = service.canStart(story, completedStories);

        assertTrue(canStart);
    }

    @Test
    void canStartReturnsFalseWhenSomeBlockersNotCompleted() {
        JiraIssueEntity story = createStory("PROJ-1", 10.0);
        story.setIsBlockedBy(List.of("PROJ-2", "PROJ-3"));

        Set<String> completedStories = Set.of("PROJ-2"); // Only PROJ-2 completed

        boolean canStart = service.canStart(story, completedStories);

        assertFalse(canStart);
    }

    @Test
    void canStartReturnsFalseWhenNoBlockersCompleted() {
        JiraIssueEntity story = createStory("PROJ-1", 10.0);
        story.setIsBlockedBy(List.of("PROJ-2"));

        Set<String> completedStories = Set.of();

        boolean canStart = service.canStart(story, completedStories);

        assertFalse(canStart);
    }

    @Test
    void canStartReturnsTrueWhenBlockedByIsNull() {
        JiraIssueEntity story = createStory("PROJ-1", 10.0);
        story.setIsBlockedBy(null);

        boolean canStart = service.canStart(story, Set.of());

        assertTrue(canStart);
    }

    @Test
    void canStartReturnsTrueWhenBlockedByIsEmpty() {
        JiraIssueEntity story = createStory("PROJ-1", 10.0);
        story.setIsBlockedBy(List.of());

        boolean canStart = service.canStart(story, Set.of());

        assertTrue(canStart);
    }

    // ==================== Build Dependency Graph Tests ====================

    @Test
    void buildDependencyGraphReturnsEmptyForNoStories() {
        StoryDependencyService.DependencyGraph graph = service.buildDependencyGraph(List.of());

        assertTrue(graph.nodes().isEmpty());
        assertTrue(graph.edges().isEmpty());
    }

    @Test
    void buildDependencyGraphReturnsNodesForIndependentStories() {
        JiraIssueEntity story1 = createStory("PROJ-1", 10.0);
        JiraIssueEntity story2 = createStory("PROJ-2", 20.0);

        StoryDependencyService.DependencyGraph graph = service.buildDependencyGraph(List.of(story1, story2));

        assertEquals(2, graph.nodes().size());
        assertTrue(graph.nodes().contains("PROJ-1"));
        assertTrue(graph.nodes().contains("PROJ-2"));
        assertTrue(graph.edges().isEmpty());
    }

    @Test
    void buildDependencyGraphCreatesEdgesForDependencies() {
        JiraIssueEntity story1 = createStory("PROJ-1", 10.0);
        story1.setBlocks(List.of("PROJ-2"));

        JiraIssueEntity story2 = createStory("PROJ-2", 20.0);
        story2.setIsBlockedBy(List.of("PROJ-1"));

        StoryDependencyService.DependencyGraph graph = service.buildDependencyGraph(List.of(story1, story2));

        assertEquals(2, graph.nodes().size());
        // Both blocks and isBlockedBy create edges, so we get 2 edges (but they're the same logically)
        assertEquals(2, graph.edges().size());

        // Both edges should be PROJ-1 -> PROJ-2
        for (StoryDependencyService.DependencyEdge edge : graph.edges()) {
            assertEquals("PROJ-1", edge.from());
            assertEquals("PROJ-2", edge.to());
            assertEquals("blocks", edge.type());
        }
    }

    @Test
    void buildDependencyGraphHandlesMultipleDependencies() {
        JiraIssueEntity story1 = createStory("PROJ-1", 10.0);
        story1.setBlocks(List.of("PROJ-2", "PROJ-3"));

        JiraIssueEntity story2 = createStory("PROJ-2", 20.0);
        story2.setIsBlockedBy(List.of("PROJ-1"));

        JiraIssueEntity story3 = createStory("PROJ-3", 30.0);
        story3.setIsBlockedBy(List.of("PROJ-1"));

        StoryDependencyService.DependencyGraph graph = service.buildDependencyGraph(List.of(story1, story2, story3));

        assertEquals(3, graph.nodes().size());
        // 2 from blocks + 2 from isBlockedBy = 4 edges
        assertEquals(4, graph.edges().size());

        // Should have edges: PROJ-1 -> PROJ-2 and PROJ-1 -> PROJ-3
        List<String> toKeys = graph.edges().stream().map(StoryDependencyService.DependencyEdge::to).toList();
        long count2 = toKeys.stream().filter(k -> k.equals("PROJ-2")).count();
        long count3 = toKeys.stream().filter(k -> k.equals("PROJ-3")).count();
        assertEquals(2, count2); // Appears twice (from blocks and isBlockedBy)
        assertEquals(2, count3); // Appears twice (from blocks and isBlockedBy)
    }

    @Test
    void buildDependencyGraphIncludesEdgesToNonExistentStories() {
        JiraIssueEntity story1 = createStory("PROJ-1", 10.0);
        story1.setBlocks(List.of("PROJ-999")); // Non-existent

        StoryDependencyService.DependencyGraph graph = service.buildDependencyGraph(List.of(story1));

        assertEquals(1, graph.nodes().size());
        // Edge is created even if target doesn't exist in the list
        // (This allows visualization of incomplete dependency graphs)
        assertEquals(1, graph.edges().size());
        assertEquals("PROJ-1", graph.edges().get(0).from());
        assertEquals("PROJ-999", graph.edges().get(0).to());
    }

    // ==================== Helper Methods ====================

    private JiraIssueEntity createStory(String key, double score) {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey(key);
        story.setIssueId(key.replace("-", ""));
        story.setProjectKey("PROJ");
        story.setSummary("Test Story: " + key);
        story.setStatus("New");
        story.setIssueType("Story");
        story.setSubtask(false);
        story.setCreatedAt(OffsetDateTime.now());
        story.setUpdatedAt(OffsetDateTime.now());
        return story;
    }
}
