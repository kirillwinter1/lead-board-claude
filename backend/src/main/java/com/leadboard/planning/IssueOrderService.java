package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing manual order of epics and stories.
 */
@Service
public class IssueOrderService {

    private static final Logger log = LoggerFactory.getLogger(IssueOrderService.class);
    private static final List<String> EPIC_TYPES = List.of("Epic", "Эпик");
    private static final List<String> STORY_TYPES = List.of("Story", "История", "Bug", "Баг");

    private final JiraIssueRepository issueRepository;

    public IssueOrderService(JiraIssueRepository issueRepository) {
        this.issueRepository = issueRepository;
    }

    /**
     * Move an epic to a new position within its team.
     *
     * @param epicKey the epic key to move
     * @param newPosition the new 1-based position (1 = first)
     * @return the updated epic entity
     */
    @Transactional
    public JiraIssueEntity reorderEpic(String epicKey, int newPosition) {
        JiraIssueEntity epic = issueRepository.findByIssueKey(epicKey)
                .orElseThrow(() -> new IllegalArgumentException("Epic not found: " + epicKey));

        if (!isEpic(epic.getIssueType())) {
            throw new IllegalArgumentException("Issue is not an epic: " + epicKey);
        }

        Long teamId = epic.getTeamId();
        if (teamId == null) {
            throw new IllegalArgumentException("Epic has no team: " + epicKey);
        }

        Integer currentOrder = epic.getManualOrder();
        if (currentOrder == null) {
            currentOrder = issueRepository.findMaxEpicOrderForTeam(teamId, EPIC_TYPES) + 1;
        }

        if (newPosition < 1) {
            newPosition = 1;
        }

        // Get max order to cap the position
        int maxOrder = issueRepository.findMaxEpicOrderForTeam(teamId, EPIC_TYPES);
        if (newPosition > maxOrder) {
            newPosition = maxOrder;
        }

        if (currentOrder == newPosition) {
            return epic;
        }

        log.info("Moving epic {} from position {} to {} in team {}", epicKey, currentOrder, newPosition, teamId);

        // Shift other epics
        if (newPosition < currentOrder) {
            // Moving up: shift items down (increase order) in range [newPosition, currentOrder)
            shiftEpicsDown(teamId, newPosition, currentOrder);
        } else {
            // Moving down: shift items up (decrease order) in range (currentOrder, newPosition]
            shiftEpicsUp(teamId, currentOrder, newPosition);
        }

        epic.setManualOrder(newPosition);
        return issueRepository.save(epic);
    }

    /**
     * Move a story/bug to a new position within its parent epic.
     *
     * @param storyKey the story/bug key to move
     * @param newPosition the new 1-based position (1 = first)
     * @return the updated story entity
     */
    @Transactional
    public JiraIssueEntity reorderStory(String storyKey, int newPosition) {
        JiraIssueEntity story = issueRepository.findByIssueKey(storyKey)
                .orElseThrow(() -> new IllegalArgumentException("Story not found: " + storyKey));

        if (!isStory(story.getIssueType())) {
            throw new IllegalArgumentException("Issue is not a story/bug: " + storyKey);
        }

        String parentKey = story.getParentKey();
        if (parentKey == null) {
            throw new IllegalArgumentException("Story has no parent: " + storyKey);
        }

        Integer currentOrder = story.getManualOrder();
        if (currentOrder == null) {
            currentOrder = issueRepository.findMaxStoryOrderForParent(parentKey, STORY_TYPES) + 1;
        }

        if (newPosition < 1) {
            newPosition = 1;
        }

        // Get max order to cap the position
        int maxOrder = issueRepository.findMaxStoryOrderForParent(parentKey, STORY_TYPES);
        if (newPosition > maxOrder) {
            newPosition = maxOrder;
        }

        if (currentOrder == newPosition) {
            return story;
        }

        log.info("Moving story {} from position {} to {} in epic {}", storyKey, currentOrder, newPosition, parentKey);

        // Shift other stories
        if (newPosition < currentOrder) {
            // Moving up: shift items down (increase order) in range [newPosition, currentOrder)
            shiftStoriesDown(parentKey, newPosition, currentOrder);
        } else {
            // Moving down: shift items up (decrease order) in range (currentOrder, newPosition]
            shiftStoriesUp(parentKey, currentOrder, newPosition);
        }

        story.setManualOrder(newPosition);
        return issueRepository.save(story);
    }

    /**
     * Assigns manual_order to a newly synced issue if it doesn't have one.
     */
    @Transactional
    public void assignOrderIfMissing(JiraIssueEntity issue) {
        if (issue.getManualOrder() != null) {
            return;
        }

        if (isEpic(issue.getIssueType()) && issue.getTeamId() != null) {
            int maxOrder = issueRepository.findMaxEpicOrderForTeam(issue.getTeamId(), EPIC_TYPES);
            issue.setManualOrder(maxOrder + 1);
            issueRepository.save(issue);
            log.debug("Assigned order {} to new epic {}", issue.getManualOrder(), issue.getIssueKey());
        } else if (isStory(issue.getIssueType()) && issue.getParentKey() != null) {
            int maxOrder = issueRepository.findMaxStoryOrderForParent(issue.getParentKey(), STORY_TYPES);
            issue.setManualOrder(maxOrder + 1);
            issueRepository.save(issue);
            log.debug("Assigned order {} to new story {}", issue.getManualOrder(), issue.getIssueKey());
        }
    }

    /**
     * Normalize epic orders for a team: fix gaps, zeros, and NULLs into contiguous 1-based sequence.
     * Preserves existing relative order. NULLs go to the end.
     */
    @Transactional
    public void normalizeTeamEpicOrders(Long teamId) {
        if (teamId == null) return;

        List<JiraIssueEntity> epics = issueRepository.findByIssueTypeInAndTeamIdOrderByManualOrderAsc(EPIC_TYPES, teamId);
        if (epics.isEmpty()) return;

        int index = 1;
        for (JiraIssueEntity epic : epics) {
            if (!java.util.Objects.equals(epic.getManualOrder(), index)) {
                epic.setManualOrder(index);
                issueRepository.save(epic);
            }
            index++;
        }
    }

    /**
     * Normalize story/bug orders within a parent epic: fix gaps, zeros, and NULLs into contiguous 1-based sequence.
     * Preserves existing relative order. NULLs go to the end. Subtasks are skipped.
     */
    @Transactional
    public void normalizeStoryOrders(String parentKey) {
        if (parentKey == null) return;

        List<JiraIssueEntity> children = issueRepository.findByParentKeyOrderByManualOrderAsc(parentKey);
        if (children.isEmpty()) return;

        int index = 1;
        for (JiraIssueEntity child : children) {
            if (!isStory(child.getIssueType())) continue;
            if (!java.util.Objects.equals(child.getManualOrder(), index)) {
                child.setManualOrder(index);
                issueRepository.save(child);
            }
            index++;
        }
    }

    private void shiftEpicsDown(Long teamId, int fromPosition, int toPosition) {
        List<JiraIssueEntity> epics = issueRepository.findByIssueTypeInAndTeamIdOrderByManualOrderAsc(EPIC_TYPES, teamId);
        for (JiraIssueEntity e : epics) {
            Integer order = e.getManualOrder();
            if (order != null && order >= fromPosition && order < toPosition) {
                e.setManualOrder(order + 1);
                issueRepository.save(e);
            }
        }
    }

    private void shiftEpicsUp(Long teamId, int fromPosition, int toPosition) {
        List<JiraIssueEntity> epics = issueRepository.findByIssueTypeInAndTeamIdOrderByManualOrderAsc(EPIC_TYPES, teamId);
        for (JiraIssueEntity e : epics) {
            Integer order = e.getManualOrder();
            if (order != null && order > fromPosition && order <= toPosition) {
                e.setManualOrder(order - 1);
                issueRepository.save(e);
            }
        }
    }

    private void shiftStoriesDown(String parentKey, int fromPosition, int toPosition) {
        List<JiraIssueEntity> stories = issueRepository.findByParentKeyOrderByManualOrderAsc(parentKey);
        for (JiraIssueEntity s : stories) {
            if (!isStory(s.getIssueType())) continue;
            Integer order = s.getManualOrder();
            if (order != null && order >= fromPosition && order < toPosition) {
                s.setManualOrder(order + 1);
                issueRepository.save(s);
            }
        }
    }

    private void shiftStoriesUp(String parentKey, int fromPosition, int toPosition) {
        List<JiraIssueEntity> stories = issueRepository.findByParentKeyOrderByManualOrderAsc(parentKey);
        for (JiraIssueEntity s : stories) {
            if (!isStory(s.getIssueType())) continue;
            Integer order = s.getManualOrder();
            if (order != null && order > fromPosition && order <= toPosition) {
                s.setManualOrder(order - 1);
                issueRepository.save(s);
            }
        }
    }

    private boolean isEpic(String issueType) {
        return EPIC_TYPES.stream().anyMatch(t -> t.equalsIgnoreCase(issueType));
    }

    private boolean isStory(String issueType) {
        return STORY_TYPES.stream().anyMatch(t -> t.equalsIgnoreCase(issueType));
    }
}
