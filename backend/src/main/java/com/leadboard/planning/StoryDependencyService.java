package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for handling story dependencies and topological sorting.
 *
 * Implements topological sort (Kahn's algorithm) to order stories
 * based on "blocks" / "is blocked by" relationships.
 */
@Service
public class StoryDependencyService {

    private static final Logger log = LoggerFactory.getLogger(StoryDependencyService.class);

    /**
     * Sorts stories using topological sort based on dependencies.
     * Stories within each "layer" (same dependency level) are sorted by AutoScore DESC.
     *
     * @param stories List of stories to sort
     * @param storyScores Map of story key -> AutoScore
     * @return Sorted list of stories
     */
    public List<JiraIssueEntity> topologicalSort(List<JiraIssueEntity> stories, Map<String, Double> storyScores) {
        // Build dependency graph
        Map<String, JiraIssueEntity> storyMap = stories.stream()
                .collect(Collectors.toMap(JiraIssueEntity::getIssueKey, s -> s));

        Map<String, Set<String>> graph = new HashMap<>(); // key -> dependencies (is blocked by)
        Map<String, Integer> inDegree = new HashMap<>();

        // Initialize
        for (JiraIssueEntity story : stories) {
            String key = story.getIssueKey();
            graph.put(key, new HashSet<>());
            inDegree.put(key, 0);
        }

        // Build graph from dependencies
        for (JiraIssueEntity story : stories) {
            String key = story.getIssueKey();
            List<String> isBlockedBy = story.getIsBlockedBy();

            if (isBlockedBy != null) {
                for (String blocker : isBlockedBy) {
                    // Only add edge if blocker is in our story list
                    if (storyMap.containsKey(blocker)) {
                        graph.get(key).add(blocker);
                        inDegree.put(key, inDegree.get(key) + 1);
                    }
                }
            }
        }

        // Topological sort using Kahn's algorithm
        List<JiraIssueEntity> sorted = new ArrayList<>();
        PriorityQueue<JiraIssueEntity> queue = new PriorityQueue<>((a, b) -> {
            // Sort by AutoScore DESC within same layer
            double scoreA = storyScores.getOrDefault(a.getIssueKey(), 0.0);
            double scoreB = storyScores.getOrDefault(b.getIssueKey(), 0.0);
            return Double.compare(scoreB, scoreA);
        });

        // Add all stories with no dependencies
        for (JiraIssueEntity story : stories) {
            if (inDegree.get(story.getIssueKey()) == 0) {
                queue.offer(story);
            }
        }

        while (!queue.isEmpty()) {
            JiraIssueEntity current = queue.poll();
            sorted.add(current);

            // Update dependencies
            for (JiraIssueEntity story : stories) {
                Set<String> deps = graph.get(story.getIssueKey());
                if (deps.contains(current.getIssueKey())) {
                    deps.remove(current.getIssueKey());
                    int newDegree = inDegree.get(story.getIssueKey()) - 1;
                    inDegree.put(story.getIssueKey(), newDegree);

                    if (newDegree == 0) {
                        queue.offer(story);
                    }
                }
            }
        }

        // Check for circular dependencies
        if (sorted.size() < stories.size()) {
            log.warn("Circular dependency detected in stories. Sorted: {}, Total: {}", sorted.size(), stories.size());

            // Add remaining stories to the end (sorted by AutoScore)
            Set<String> sortedKeys = sorted.stream()
                    .map(JiraIssueEntity::getIssueKey)
                    .collect(Collectors.toSet());

            List<JiraIssueEntity> remaining = stories.stream()
                    .filter(s -> !sortedKeys.contains(s.getIssueKey()))
                    .sorted((a, b) -> {
                        double scoreA = storyScores.getOrDefault(a.getIssueKey(), 0.0);
                        double scoreB = storyScores.getOrDefault(b.getIssueKey(), 0.0);
                        return Double.compare(scoreB, scoreA);
                    })
                    .toList();

            sorted.addAll(remaining);
        }

        return sorted;
    }

    /**
     * Detects circular dependencies in stories.
     *
     * @param stories List of stories
     * @return List of story keys involved in circular dependencies
     */
    public List<String> detectCircularDependencies(List<JiraIssueEntity> stories) {
        Map<String, Set<String>> graph = new HashMap<>();

        // Build graph
        for (JiraIssueEntity story : stories) {
            String key = story.getIssueKey();
            graph.put(key, new HashSet<>());

            List<String> isBlockedBy = story.getIsBlockedBy();
            if (isBlockedBy != null) {
                graph.get(key).addAll(isBlockedBy);
            }
        }

        // DFS to detect cycles
        Set<String> visited = new HashSet<>();
        Set<String> recStack = new HashSet<>();
        List<String> cycleNodes = new ArrayList<>();

        for (String key : graph.keySet()) {
            if (hasCycleDFS(key, graph, visited, recStack, cycleNodes)) {
                break;
            }
        }

        return cycleNodes;
    }

    private boolean hasCycleDFS(String node, Map<String, Set<String>> graph, Set<String> visited, Set<String> recStack, List<String> cycleNodes) {
        visited.add(node);
        recStack.add(node);

        Set<String> neighbors = graph.get(node);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    if (hasCycleDFS(neighbor, graph, visited, recStack, cycleNodes)) {
                        cycleNodes.add(node);
                        return true;
                    }
                } else if (recStack.contains(neighbor)) {
                    // Cycle detected
                    cycleNodes.add(node);
                    cycleNodes.add(neighbor);
                    return true;
                }
            }
        }

        recStack.remove(node);
        return false;
    }

    /**
     * Checks if a story can start (not blocked by incomplete stories).
     *
     * @param story Story to check
     * @param completedStories Set of completed story keys
     * @return true if story can start
     */
    public boolean canStart(JiraIssueEntity story, Set<String> completedStories) {
        List<String> isBlockedBy = story.getIsBlockedBy();

        if (isBlockedBy == null || isBlockedBy.isEmpty()) {
            return true;
        }

        // Story can start if all blocking stories are completed
        return completedStories.containsAll(isBlockedBy);
    }

    /**
     * Builds dependency graph for visualization.
     *
     * @param stories List of stories
     * @return Dependency graph with nodes and edges
     */
    public DependencyGraph buildDependencyGraph(List<JiraIssueEntity> stories) {
        List<String> nodes = stories.stream()
                .map(JiraIssueEntity::getIssueKey)
                .toList();

        List<DependencyEdge> edges = new ArrayList<>();

        for (JiraIssueEntity story : stories) {
            String from = story.getIssueKey();

            // Add "blocks" edges
            List<String> blocks = story.getBlocks();
            if (blocks != null) {
                for (String to : blocks) {
                    edges.add(new DependencyEdge(from, to, "blocks"));
                }
            }

            // Add "is blocked by" edges
            List<String> isBlockedBy = story.getIsBlockedBy();
            if (isBlockedBy != null) {
                for (String blocker : isBlockedBy) {
                    edges.add(new DependencyEdge(blocker, from, "blocks"));
                }
            }
        }

        return new DependencyGraph(nodes, edges);
    }

    public record DependencyGraph(List<String> nodes, List<DependencyEdge> edges) {}

    public record DependencyEdge(String from, String to, String type) {}
}
