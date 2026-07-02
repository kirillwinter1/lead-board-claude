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
        when(issues.findByParentKey("LB-1")).thenReturn(List.of(child));
        when(issues.findByParentKey("LB-2")).thenReturn(List.of(subtask));
        when(issues.findByParentKey("LB-3")).thenReturn(List.of());
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
}
