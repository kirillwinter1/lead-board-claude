package com.leadboard.quality.fix;

import com.leadboard.config.entity.BoardCategory;
import com.leadboard.config.service.JiraMetadataService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.jira.JiraWorklogResponse;
import com.leadboard.jira.JiraWriteService;
import com.leadboard.quality.DataQualityService;
import com.leadboard.quality.fix.dto.FixPreview;
import com.leadboard.quality.fix.dto.FixResult;
import com.leadboard.quality.fix.handlers.*;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.sync.SyncService;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.team.TeamMemberRepository;
import com.leadboard.team.TeamRepository;
import com.leadboard.team.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FixHandlersTest {

    @Mock private JiraWriteService jiraWrite;
    @Mock private JiraClient jiraClient;
    @Mock private WorkflowConfigService wfc;
    @Mock private DataQualityService dqs;
    @Mock private JiraIssueRepository issues;
    @Mock private TeamMemberRepository members;
    @Mock private TeamRepository teams;
    @Mock private TeamService teamService;
    @Mock private SyncService syncService;
    @Mock private JiraMetadataService metadata;

    private FixSupport support;

    @BeforeEach
    void setUp() {
        support = new FixSupport(jiraWrite, jiraClient, wfc, dqs, issues, members, teams, teamService,
                syncService, metadata);
        lenient().when(jiraWrite.hasUserCreds()).thenReturn(true);
    }

    private JiraIssueEntity issue(String key, String type, String status) {
        JiraIssueEntity e = new JiraIssueEntity();
        e.setIssueKey(key);
        e.setProjectKey("LB");
        e.setIssueType(type);
        e.setStatus(status);
        e.setSummary(key + " summary");
        return e;
    }

    // ---------- Group A ----------

    @Test
    void childInProgressEpicNot_movesEpic() {
        JiraIssueEntity child = issue("LB-2", "Story", "In Progress");
        child.setParentKey("LB-1");
        JiraIssueEntity epic = issue("LB-1", "Epic", "New");
        when(issues.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
        when(wfc.categorizeIssueType("Epic", "LB")).thenReturn(BoardCategory.EPIC);
        when(wfc.getFirstStatusNameForCategory(StatusCategory.IN_PROGRESS, BoardCategory.EPIC)).thenReturn("In Progress");
        when(jiraWrite.transitionWithFallback("LB-1", "In Progress")).thenReturn("In Progress");

        var handler = new ChildInProgressEpicNotFixHandler(support);

        FixPreview preview = handler.preview(child);
        assertEquals("LB-1", preview.changes().get(0).issueKey()); // change points at epic, not child
        assertEquals("Epic", preview.changes().get(0).issueType()); // carries the epic's type for the preview icon

        FixResult result = handler.apply(child, null, Map.of());
        assertTrue(result.success());
        assertEquals(List.of("LB-1"), result.updatedIssues());
        verify(jiraWrite).transitionWithFallback("LB-1", "In Progress");
    }

    @Test
    void subtaskDoneNoTimeLogged_notApplicableWithoutEstimate() {
        JiraIssueEntity sub = issue("LB-5", "Sub-task", "Done");
        var handler = new SubtaskDoneNoTimeLoggedFixHandler(support);

        FixPreview preview = handler.preview(sub);
        assertFalse(preview.applicable());
        assertThrows(IllegalArgumentException.class, () -> handler.apply(sub, null, Map.of()));
    }

    @Test
    void subtaskDoneNoTimeLogged_logsEstimate() {
        JiraIssueEntity sub = issue("LB-5", "Sub-task", "Done");
        sub.setOriginalEstimateSeconds(7200L);
        var handler = new SubtaskDoneNoTimeLoggedFixHandler(support);

        FixResult result = handler.apply(sub, null, Map.of());
        assertTrue(result.success());
        verify(jiraWrite).logWorkWithFallback(eq("LB-5"), eq(7200), any(LocalDate.class));
    }

    // ---------- Group B ----------

    @Test
    void epicNoTeam_isLocalAndSetsManualFlag() {
        JiraIssueEntity epic = issue("LB-1", "Epic", "New");
        TeamEntity team = new TeamEntity();
        team.setId(9L);
        team.setName("Alpha");
        when(teams.findByActiveTrue()).thenReturn(List.of(team));
        when(teams.findByIdAndActiveTrue(9L)).thenReturn(Optional.of(team));

        var handler = new EpicNoTeamFixHandler(support);
        assertTrue(handler.local());

        FixPreview preview = handler.preview(epic);
        assertEquals("LOCAL", preview.authMode());
        assertEquals(1, preview.inputs().size());

        FixResult result = handler.apply(epic, null, Map.of("teamId", "9"));
        assertTrue(result.success());
        assertEquals(9L, epic.getTeamId());
        assertTrue(epic.isTeamIdManual());
        verify(issues).save(epic);
    }

    @Test
    void teamFieldUnmapped_onlyOffersTeamsWithBlankJiraValue() {
        JiraIssueEntity epic = issue("LB-1", "Epic", "New");
        epic.setTeamFieldValue("Squad-X");
        TeamEntity blank = new TeamEntity();
        blank.setId(1L);
        blank.setName("Blank");
        blank.setJiraTeamValue(null);
        TeamEntity mapped = new TeamEntity();
        mapped.setId(2L);
        mapped.setName("Mapped");
        mapped.setJiraTeamValue("Existing");
        when(teams.findByActiveTrue()).thenReturn(List.of(blank, mapped));
        when(teams.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(blank));

        var handler = new TeamFieldUnmappedFixHandler(support);
        assertTrue(handler.local());

        FixPreview preview = handler.preview(epic);
        assertEquals(1, preview.inputs().get(0).options().size());
        assertEquals("1", preview.inputs().get(0).options().get(0).value());

        handler.apply(epic, null, Map.of("teamId", "1"));
        verify(teamService).updateTeam(eq(1L), any());
    }

    @Test
    void inProgressNoAssignee_notApplicableWhenEpicHasNoTeam() {
        JiraIssueEntity sub = issue("LB-5", "Sub-task", "In Progress");
        sub.setParentKey("LB-2");
        JiraIssueEntity story = issue("LB-2", "Story", "In Progress");
        story.setParentKey("LB-1");
        JiraIssueEntity epic = issue("LB-1", "Epic", "In Progress");
        when(issues.findByIssueKey("LB-2")).thenReturn(Optional.of(story));
        when(issues.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
        when(wfc.categorizeIssueType("Story", "LB")).thenReturn(BoardCategory.STORY);

        var handler = new InProgressNoAssigneeFixHandler(support);
        FixPreview preview = handler.preview(sub);
        assertFalse(preview.applicable());
    }

    @Test
    void assigneeNotInTeam_addToTeamIsLocalReassignSyncs() {
        JiraIssueEntity sub = issue("LB-5", "Sub-task", "In Progress");
        sub.setParentKey("LB-2");
        sub.setAssigneeAccountId("acc-x");
        sub.setAssigneeDisplayName("X User");
        JiraIssueEntity story = issue("LB-2", "Story", "In Progress");
        story.setParentKey("LB-1");
        JiraIssueEntity epic = issue("LB-1", "Epic", "In Progress");
        epic.setTeamId(3L);
        when(issues.findByIssueKey("LB-2")).thenReturn(Optional.of(story));
        when(issues.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
        when(wfc.categorizeIssueType("Story", "LB")).thenReturn(BoardCategory.STORY);

        var handler = new AssigneeNotInTeamFixHandler(support);

        FixPreview preview = handler.preview(sub);
        assertEquals(2, preview.choices().size());

        FixResult addToTeam = handler.apply(sub, "addToTeam", Map.of());
        assertTrue(addToTeam.updatedIssues().isEmpty()); // local membership change, no re-sync
        verify(teamService).addTeamMember(eq(3L), any());

        when(members.existsByJiraAccountIdAndTeamIdAndActiveTrue("acc-y", 3L)).thenReturn(true);
        FixResult reassign = handler.apply(sub, "reassign", Map.of("accountId", "acc-y"));
        assertEquals(List.of("LB-5"), reassign.updatedIssues());
        verify(jiraWrite).assignWithFallback("LB-5", "acc-y");

        // A non-member account id must be rejected, not silently assigned
        assertThrows(IllegalArgumentException.class,
                () -> handler.apply(sub, "reassign", Map.of("accountId", "acc-stranger")));
    }

    @Test
    void estimateHandler_convertsHoursToSeconds() {
        JiraIssueEntity sub = issue("LB-5", "Sub-task", "New");
        var handler = new SubtaskNoEstimateFixHandler(support);

        handler.apply(sub, null, Map.of("hours", "2.5"));
        verify(jiraClient).updateEstimate("LB-5", 9000); // 2.5h * 3600
    }

    @Test
    void epicNoDueDate_setsDueDate() {
        JiraIssueEntity epic = issue("LB-1", "Epic", "Planned");
        var handler = new EpicNoDueDateFixHandler(support);

        handler.apply(epic, null, Map.of("dueDate", "2026-09-01"));
        verify(jiraClient).updateDueDate("LB-1", LocalDate.of(2026, 9, 1));
    }

    @Test
    void bugNoPriority_fallsBackToStandardFiveWhenMetadataEmpty() {
        JiraIssueEntity bug = issue("LB-9", "Bug", "New");
        when(metadata.getPriorities()).thenReturn(List.of());
        var handler = new BugNoPriorityFixHandler(support);

        FixPreview preview = handler.preview(bug);
        assertEquals(5, preview.inputs().get(0).options().size());

        handler.apply(bug, null, Map.of("priority", "High"));
        verify(jiraClient).updatePriority("LB-9", "High");
    }

    // ---------- Group C ----------

    @Test
    void epicDoneOpenChildren_closesBottomUpAndReportsPartial() {
        JiraIssueEntity epic = issue("LB-1", "Epic", "Done");
        JiraIssueEntity child = issue("LB-2", "Story", "In Progress");
        JiraIssueEntity subtask = issue("LB-3", "Sub-task", "In Progress");
        subtask.setParentKey("LB-2");
        when(issues.findByParentKey("LB-1")).thenReturn(List.of(child));
        when(issues.findByParentKeyIn(List.of("LB-2"))).thenReturn(List.of(subtask));
        when(wfc.isDone(anyString(), anyString(), anyString())).thenReturn(false);
        when(wfc.categorizeIssueType("Story", "LB")).thenReturn(BoardCategory.STORY);
        when(wfc.categorizeIssueType("Sub-task", "LB")).thenReturn(BoardCategory.SUBTASK);
        when(wfc.getFirstStatusNameForCategory(StatusCategory.DONE, BoardCategory.STORY)).thenReturn("Done");
        when(wfc.getFirstStatusNameForCategory(StatusCategory.DONE, BoardCategory.SUBTASK)).thenReturn("Done");
        when(jiraWrite.transitionWithFallback("LB-3", "Done")).thenReturn("Done");
        when(jiraWrite.transitionWithFallback("LB-2", "Done")).thenThrow(new RuntimeException("boom"));

        var handler = new EpicDoneOpenChildrenFixHandler(support);

        FixPreview preview = handler.preview(epic);
        assertTrue(preview.risky());
        assertEquals(List.of("LB-3", "LB-2"), preview.affectedIssues());

        FixResult result = handler.apply(epic, null, Map.of());
        assertFalse(result.success()); // partial: LB-2 failed
        assertEquals(List.of("LB-3"), result.updatedIssues());

        // bottom-up: subtask closed before its parent story
        InOrder inOrder = inOrder(jiraWrite);
        inOrder.verify(jiraWrite).transitionWithFallback("LB-3", "Done");
        inOrder.verify(jiraWrite).transitionWithFallback("LB-2", "Done");
    }

    @Test
    void storyFullyLoggedNotDone_isRiskyTransition() {
        JiraIssueEntity story = issue("LB-2", "Story", "In Progress");
        when(wfc.categorizeIssueType("Story", "LB")).thenReturn(BoardCategory.STORY);
        when(wfc.getFirstStatusNameForCategory(StatusCategory.DONE, BoardCategory.STORY)).thenReturn("Done");
        when(jiraWrite.transitionWithFallback("LB-2", "Done")).thenReturn("Done");

        var handler = new StoryFullyLoggedNotDoneFixHandler(support);
        FixPreview preview = handler.preview(story);
        assertTrue(preview.risky());

        FixResult result = handler.apply(story, null, Map.of());
        assertTrue(result.success());
        verify(jiraWrite).transitionWithFallback("LB-2", "Done");
    }

    @Test
    void timeLoggedNotInSubtask_notApplicableWithoutSubtask() {
        JiraIssueEntity story = issue("LB-2", "Story", "In Progress");
        story.setTimeSpentSeconds(3600L);
        when(issues.findByParentKey("LB-2")).thenReturn(List.of());

        var handler = new TimeLoggedNotInSubtaskFixHandler(support);
        FixPreview preview = handler.preview(story);
        assertFalse(preview.applicable());
    }

    @Test
    void timeLoggedNotInSubtask_movesWorklogAddBeforeDelete() {
        JiraIssueEntity story = issue("LB-2", "Story", "In Progress");
        story.setTimeSpentSeconds(3600L);
        JiraIssueEntity sub = issue("LB-3", "Sub-task", "In Progress");
        sub.setSubtask(true);
        when(issues.findByParentKey("LB-2")).thenReturn(List.of(sub));

        JiraWorklogResponse.WorklogEntry w = new JiraWorklogResponse.WorklogEntry();
        w.setId("w1");
        w.setTimeSpentSeconds(3600);
        w.setStarted("2026-01-15T10:00:00.000+0000");
        when(jiraClient.fetchIssueWorklogs("LB-2")).thenReturn(List.of(w));

        var handler = new TimeLoggedNotInSubtaskFixHandler(support);

        FixPreview preview = handler.preview(story);
        assertTrue(preview.applicable());
        assertTrue(preview.risky());

        FixResult result = handler.apply(story, null, Map.of("targetSubtaskKey", "LB-3"));
        assertTrue(result.success());

        InOrder inOrder = inOrder(jiraClient);
        inOrder.verify(jiraClient).addWorklogAt("LB-3", 3600, "2026-01-15T10:00:00.000+0000");
        inOrder.verify(jiraClient).deleteWorklog("LB-2", "w1");
    }

    // ---------- Coverage for the remaining handlers ----------

    @Test
    void storyTodoButHasWork_movesStoryToInProgress() {
        JiraIssueEntity story = issue("LB-2", "Story", "New");
        when(wfc.categorizeIssueType("Story", "LB")).thenReturn(BoardCategory.STORY);
        when(wfc.getFirstStatusNameForCategory(StatusCategory.IN_PROGRESS, BoardCategory.STORY)).thenReturn("Аналитика");
        when(jiraWrite.transitionWithFallback("LB-2", "Аналитика")).thenReturn("Аналитика");

        var handler = new StoryTodoButHasWorkFixHandler(support);

        FixPreview preview = handler.preview(story);
        assertTrue(preview.applicable());
        assertEquals("Аналитика", preview.changes().get(0).to());

        FixResult result = handler.apply(story, null, Map.of());
        assertTrue(result.success());
        assertEquals(List.of("LB-2"), result.updatedIssues());
        verify(jiraWrite).transitionWithFallback("LB-2", "Аналитика");
    }

    @Test
    void subtaskInProgressStoryNot_movesParentStoryNotSubtask() {
        JiraIssueEntity sub = issue("LB-5", "Sub-task", "In Progress");
        sub.setParentKey("LB-2");
        JiraIssueEntity story = issue("LB-2", "Story", "New");
        when(issues.findByIssueKey("LB-2")).thenReturn(Optional.of(story));
        when(wfc.categorizeIssueType("Story", "LB")).thenReturn(BoardCategory.STORY);
        when(wfc.getFirstStatusNameForCategory(StatusCategory.IN_PROGRESS, BoardCategory.STORY)).thenReturn("Аналитика");
        when(jiraWrite.transitionWithFallback("LB-2", "Аналитика")).thenReturn("Аналитика");

        var handler = new SubtaskInProgressStoryNotFixHandler(support);

        FixPreview preview = handler.preview(sub);
        assertEquals("LB-2", preview.changes().get(0).issueKey()); // targets the story, not the subtask
        assertEquals("Story", preview.changes().get(0).issueType());

        FixResult result = handler.apply(sub, null, Map.of());
        assertEquals(List.of("LB-2"), result.updatedIssues());
        verify(jiraWrite).transitionWithFallback("LB-2", "Аналитика");
    }

    @Test
    void subtaskInProgressStoryNot_notApplicableWithoutParent() {
        JiraIssueEntity sub = issue("LB-5", "Sub-task", "In Progress");
        sub.setParentKey("LB-404");
        when(issues.findByIssueKey("LB-404")).thenReturn(Optional.empty());

        var handler = new SubtaskInProgressStoryNotFixHandler(support);
        assertFalse(handler.preview(sub).applicable());
    }

    @Test
    void subtaskTimeLoggedButTodo_movesSubtaskItself() {
        JiraIssueEntity sub = issue("LB-5", "Sub-task", "New");
        when(wfc.categorizeIssueType("Sub-task", "LB")).thenReturn(BoardCategory.SUBTASK);
        when(wfc.getFirstStatusNameForCategory(StatusCategory.IN_PROGRESS, BoardCategory.SUBTASK)).thenReturn("In Progress");
        when(jiraWrite.transitionWithFallback("LB-5", "In Progress")).thenReturn("In Progress");

        var handler = new SubtaskTimeLoggedButTodoFixHandler(support);

        FixPreview preview = handler.preview(sub);
        assertEquals("LB-5", preview.changes().get(0).issueKey());

        FixResult result = handler.apply(sub, null, Map.of());
        assertEquals(List.of("LB-5"), result.updatedIssues());
        verify(jiraWrite).transitionWithFallback("LB-5", "In Progress");
    }

    @Test
    void subtaskWorkNoEstimate_convertsHoursToSeconds() {
        JiraIssueEntity sub = issue("LB-5", "Sub-task", "In Progress");
        var handler = new SubtaskWorkNoEstimateFixHandler(support);

        handler.apply(sub, null, Map.of("hours", "1.5"));
        verify(jiraClient).updateEstimate("LB-5", 5400);
    }

    @Test
    void childDueAfterEpic_bothChoicesUpdateTheChosenIssue() {
        JiraIssueEntity story = issue("LB-2", "Story", "In Progress");
        story.setParentKey("LB-1");
        story.setDueDate(LocalDate.of(2026, 8, 20));
        JiraIssueEntity epic = issue("LB-1", "Epic", "In Progress");
        epic.setDueDate(LocalDate.of(2026, 8, 1));
        when(issues.findByIssueKey("LB-1")).thenReturn(Optional.of(epic));
        when(wfc.categorizeIssueType("Epic", "LB")).thenReturn(BoardCategory.EPIC);

        var handler = new ChildDueAfterEpicFixHandler(support);

        FixPreview preview = handler.preview(story);
        assertEquals(2, preview.choices().size());
        assertEquals("Epic", preview.choices().get(1).changes().get(0).issueType());

        FixResult moveStory = handler.apply(story, "moveStory", Map.of("dueDate", "2026-08-01"));
        assertEquals(List.of("LB-2"), moveStory.updatedIssues());
        verify(jiraClient).updateDueDate("LB-2", LocalDate.of(2026, 8, 1));

        FixResult moveEpic = handler.apply(story, "moveEpic", Map.of("dueDate", "2026-08-20"));
        assertEquals(List.of("LB-1"), moveEpic.updatedIssues());
        verify(jiraClient).updateDueDate("LB-1", LocalDate.of(2026, 8, 20));
    }

    @Test
    void storyDoneOpenChildren_continueOnErrorReportsPartial() {
        JiraIssueEntity story = issue("LB-2", "Story", "Done");
        JiraIssueEntity ok = issue("LB-3", "Sub-task", "In Progress");
        JiraIssueEntity bad = issue("LB-4", "Sub-task", "In Progress");
        when(issues.findByParentKey("LB-2")).thenReturn(List.of(ok, bad));
        when(wfc.isDone(anyString(), anyString(), anyString())).thenReturn(false);
        when(wfc.categorizeIssueType("Sub-task", "LB")).thenReturn(BoardCategory.SUBTASK);
        when(wfc.getFirstStatusNameForCategory(StatusCategory.DONE, BoardCategory.SUBTASK)).thenReturn("Done");
        when(jiraWrite.transitionWithFallback("LB-3", "Done")).thenReturn("Done");
        when(jiraWrite.transitionWithFallback("LB-4", "Done")).thenThrow(new RuntimeException("boom"));

        var handler = new StoryDoneOpenChildrenFixHandler(support);

        FixPreview preview = handler.preview(story);
        assertTrue(preview.risky());
        assertEquals(List.of("LB-3", "LB-4"), preview.affectedIssues());

        FixResult result = handler.apply(story, null, Map.of());
        assertFalse(result.success()); // partial: LB-4 failed
        assertEquals(List.of("LB-3"), result.updatedIssues());
        assertTrue(result.message().contains("LB-4"));
    }
}
