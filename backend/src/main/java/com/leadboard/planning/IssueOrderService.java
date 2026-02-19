package com.leadboard.planning;

import com.leadboard.config.service.WorkflowConfigService;
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

    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;

    public IssueOrderService(JiraIssueRepository issueRepository, WorkflowConfigService workflowConfigService) {
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
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

        if (!workflowConfigService.isEpic(epic.getIssueType())) {
            throw new IllegalArgumentException("Issue is not an epic: " + epicKey);
        }

        Long teamId = epic.getTeamId();
        if (teamId == null) {
            throw new IllegalArgumentException("Epic has no team: " + epicKey);
        }

        Integer currentOrder = epic.getManualOrder();
        if (currentOrder == null) {
            currentOrder = issueRepository.findMaxEpicOrderForTeam(teamId) + 1;
        }

        if (newPosition < 1) {
            newPosition = 1;
        }

        // Get max order to cap the position
        int maxOrder = issueRepository.findMaxEpicOrderForTeam(teamId);
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

        if (!workflowConfigService.isStoryOrBug(story.getIssueType())) {
            throw new IllegalArgumentException("Issue is not a story/bug: " + storyKey);
        }

        String parentKey = story.getParentKey();
        if (parentKey == null) {
            throw new IllegalArgumentException("Story has no parent: " + storyKey);
        }

        Integer currentOrder = story.getManualOrder();
        if (currentOrder == null) {
            currentOrder = issueRepository.findMaxStoryOrderForParent(parentKey) + 1;
        }

        if (newPosition < 1) {
            newPosition = 1;
        }

        // Get max order to cap the position
        int maxOrder = issueRepository.findMaxStoryOrderForParent(parentKey);
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

        if (workflowConfigService.isEpic(issue.getIssueType()) && issue.getTeamId() != null) {
            int maxOrder = issueRepository.findMaxEpicOrderForTeam(issue.getTeamId());
            issue.setManualOrder(maxOrder + 1);
            issueRepository.save(issue);
            log.debug("Assigned order {} to new epic {}", issue.getManualOrder(), issue.getIssueKey());
        } else if (workflowConfigService.isStoryOrBug(issue.getIssueType()) && issue.getParentKey() != null) {
            int maxOrder = issueRepository.findMaxStoryOrderForParent(issue.getParentKey());
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

        List<JiraIssueEntity> epics = issueRepository.findEpicsByTeamOrderByManualOrder(teamId);
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
            if (!workflowConfigService.isStoryOrBug(child.getIssueType())) continue;
            if (!java.util.Objects.equals(child.getManualOrder(), index)) {
                child.setManualOrder(index);
                issueRepository.save(child);
            }
            index++;
        }
    }

    private void shiftEpicsDown(Long teamId, int fromPosition, int toPosition) {
        List<JiraIssueEntity> epics = issueRepository.findEpicsByTeamOrderByManualOrder(teamId);
        for (JiraIssueEntity e : epics) {
            Integer order = e.getManualOrder();
            if (order != null && order >= fromPosition && order < toPosition) {
                e.setManualOrder(order + 1);
                issueRepository.save(e);
            }
        }
    }

    private void shiftEpicsUp(Long teamId, int fromPosition, int toPosition) {
        List<JiraIssueEntity> epics = issueRepository.findEpicsByTeamOrderByManualOrder(teamId);
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
            if (!workflowConfigService.isStoryOrBug(s.getIssueType())) continue;
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
            if (!workflowConfigService.isStoryOrBug(s.getIssueType())) continue;
            Integer order = s.getManualOrder();
            if (order != null && order > fromPosition && order <= toPosition) {
                s.setManualOrder(order - 1);
                issueRepository.save(s);
            }
        }
    }

}
