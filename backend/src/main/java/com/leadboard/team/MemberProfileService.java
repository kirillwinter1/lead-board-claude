package com.leadboard.team;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.dto.MemberProfileResponse;
import com.leadboard.team.dto.MemberProfileResponse.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemberProfileService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final JiraIssueRepository issueRepository;
    private final WorkflowConfigService workflowConfigService;
    private final WorkCalendarService workCalendarService;
    private final MemberAnalyticsService analytics;

    public MemberProfileService(
            TeamRepository teamRepository,
            TeamMemberRepository memberRepository,
            JiraIssueRepository issueRepository,
            WorkflowConfigService workflowConfigService,
            WorkCalendarService workCalendarService,
            MemberAnalyticsService analytics
    ) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.issueRepository = issueRepository;
        this.workflowConfigService = workflowConfigService;
        this.workCalendarService = workCalendarService;
        this.analytics = analytics;
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse getMemberProfile(Long teamId, Long memberId, LocalDate from, LocalDate to) {
        TeamEntity team = teamRepository.findByIdAndActiveTrue(teamId)
                .orElseThrow(() -> new TeamService.TeamNotFoundException("Team not found: " + teamId));

        TeamMemberEntity member = memberRepository.findByIdAndTeamIdAndActiveTrue(memberId, teamId)
                .orElseThrow(() -> new TeamService.TeamMemberNotFoundException("Team member not found: " + memberId));

        String accountId = member.getJiraAccountId();

        // Completed subtasks in the period
        OffsetDateTime fromDt = from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime toDt = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        List<JiraIssueEntity> completedIssues = issueRepository.findCompletedSubtasksByAssigneeInPeriod(
                accountId, teamId, fromDt, toDt);

        // All subtasks for active/upcoming
        List<JiraIssueEntity> allSubtasks = issueRepository.findSubtasksByAssigneeAndTeam(accountId, teamId);

        // Epic info cache: parentKey -> parent issue, grandparentKey -> epic issue
        Map<String, JiraIssueEntity> issueCache = new HashMap<>();

        // Build completed tasks
        List<CompletedTask> completedTasks = completedIssues.stream()
                .map(issue -> buildCompletedTask(issue, issueCache))
                .toList();

        // Build active and upcoming tasks
        List<ActiveTask> activeTasks = new ArrayList<>();
        List<ActiveTask> upcomingTasks = new ArrayList<>();

        for (JiraIssueEntity issue : allSubtasks) {
            if (issue.getDoneAt() != null) continue; // skip completed

            StatusCategory statusCat = workflowConfigService.categorize(issue.getStatus(), issue.getIssueType());
            if (statusCat == StatusCategory.IN_PROGRESS || statusCat == StatusCategory.PLANNED || statusCat == StatusCategory.DEV_DONE) {
                activeTasks.add(buildActiveTask(issue, issueCache));
            } else if (statusCat.isNotStarted()) {
                upcomingTasks.add(buildActiveTask(issue, issueCache));
            }
        }

        // Weekly trend
        List<WeeklyTrend> weeklyTrend = analytics.buildWeeklyTrend(completedIssues, to);

        // Summary
        MemberSummary summary = analytics.buildSummary(completedIssues, member.getHoursPerDay(), from, to);

        // Member info
        MemberInfo memberInfo = new MemberInfo(
                member.getId(),
                member.getDisplayName(),
                member.getRole(),
                member.getGrade().name(),
                member.getHoursPerDay(),
                team.getName(),
                team.getId(),
                member.getAvatarUrl()
        );

        return new MemberProfileResponse(memberInfo, completedTasks, activeTasks, upcomingTasks, weeklyTrend, summary);
    }

    private CompletedTask buildCompletedTask(JiraIssueEntity issue, Map<String, JiraIssueEntity> cache) {
        BigDecimal estimateH = analytics.secondsToHours(issue.getOriginalEstimateSeconds());
        BigDecimal spentH = analytics.secondsToHours(issue.getTimeSpentSeconds());
        BigDecimal dsr = analytics.calculateDsr(issue);

        LocalDate doneDate = issue.getDoneAt() != null ? issue.getDoneAt().toLocalDate() : null;

        String[] epicInfo = analytics.resolveEpicInfo(issue, cache);

        return new CompletedTask(
                issue.getIssueKey(),
                issue.getSummary(),
                epicInfo[0],
                epicInfo[1],
                estimateH,
                spentH,
                dsr,
                doneDate
        );
    }

    private ActiveTask buildActiveTask(JiraIssueEntity issue, Map<String, JiraIssueEntity> cache) {
        BigDecimal estimateH = analytics.secondsToHours(issue.getOriginalEstimateSeconds());
        BigDecimal spentH = analytics.secondsToHours(issue.getTimeSpentSeconds());

        String[] epicInfo = analytics.resolveEpicInfo(issue, cache);

        return new ActiveTask(
                issue.getIssueKey(),
                issue.getSummary(),
                epicInfo[0],
                epicInfo[1],
                estimateH,
                spentH,
                issue.getStatus()
        );
    }
}
