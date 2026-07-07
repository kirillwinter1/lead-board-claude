package com.leadboard.team;

import com.leadboard.calendar.WorkCalendarService;
import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.metrics.repository.IssueWorklogRepository;
import com.leadboard.status.StatusCategory;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.dto.AbsenceDto;
import com.leadboard.team.dto.MyWorkResponse;
import com.leadboard.team.dto.MyWorkResponse.MyMemberInfo;
import com.leadboard.team.dto.MyWorkResponse.MyTask;
import com.leadboard.team.dto.MyWorkResponse.QueueStory;
import com.leadboard.team.dto.MyWorkResponse.TeamRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Personal work desk — F88 "My Work". Aggregates active/upcoming tasks, team queue,
 * worklog calendar and analytics across every active membership of a Jira account.
 *
 * teamQueue / worklogCalendar / analytics are filled in by follow-up tasks (3-5);
 * this skeleton returns empty placeholders for them.
 */
@Service
public class MyWorkService {

    private final TeamMemberRepository memberRepository;
    private final JiraIssueRepository issueRepository;
    private final IssueWorklogRepository worklogRepository;
    private final MemberAbsenceRepository absenceRepository;
    private final AbsenceService absenceService;
    private final WorkflowConfigService workflowConfigService;
    private final WorkCalendarService workCalendarService;
    private final MemberAnalyticsService analytics;
    private final JiraConfigResolver jiraConfigResolver;

    public MyWorkService(TeamMemberRepository memberRepository, JiraIssueRepository issueRepository,
                          IssueWorklogRepository worklogRepository, MemberAbsenceRepository absenceRepository,
                          AbsenceService absenceService, WorkflowConfigService workflowConfigService,
                          WorkCalendarService workCalendarService, MemberAnalyticsService analytics,
                          JiraConfigResolver jiraConfigResolver) {
        this.memberRepository = memberRepository;
        this.issueRepository = issueRepository;
        this.worklogRepository = worklogRepository;
        this.absenceRepository = absenceRepository;
        this.absenceService = absenceService;
        this.workflowConfigService = workflowConfigService;
        this.workCalendarService = workCalendarService;
        this.analytics = analytics;
        this.jiraConfigResolver = jiraConfigResolver;
    }

    @Transactional(readOnly = true)
    public MyWorkResponse getMyWork(String accountId, LocalDate from, LocalDate to, Long teamId) {
        return getMyWork(accountId, from, to, teamId, LocalDate.now());
    }

    // package-private перегрузка с today — для детерминированных тестов календаря (Task 4/5)
    MyWorkResponse getMyWork(String accountId, LocalDate from, LocalDate to, Long teamId, LocalDate today) {
        List<TeamMemberEntity> members = memberRepository.findAllByJiraAccountIdAndActiveTrue(accountId);
        if (members.isEmpty()) {
            return new MyWorkResponse(false, null, List.of(), List.of(), List.of(), List.of(), List.of(), null);
        }

        TeamMemberEntity primary = members.get(0);
        List<TeamRef> teams = members.stream()
                .map(m -> new TeamRef(m.getTeam().getId(), m.getTeam().getName(), m.getTeam().getColor()))
                .toList();
        MyMemberInfo memberInfo = new MyMemberInfo(
                primary.getDisplayName(),
                primary.getAvatarUrl(),
                primary.getRole(),
                primary.getGrade() != null ? primary.getGrade().name() : null,
                primary.getHoursPerDay(),
                teams
        );

        List<AbsenceDto> upcomingAbsences = new ArrayList<>();
        for (TeamMemberEntity m : members) {
            upcomingAbsences.addAll(absenceService.getUpcomingAbsences(m.getId()));
        }

        List<TeamMemberEntity> taskMembers = teamId == null
                ? members
                : members.stream().filter(m -> m.getTeam().getId().equals(teamId)).toList();

        Map<String, JiraIssueEntity> issueCache = new HashMap<>();
        List<MyTask> activeTasks = new ArrayList<>();
        List<MyTask> upcomingAssigned = new ArrayList<>();

        for (TeamMemberEntity m : taskMembers) {
            List<JiraIssueEntity> subtasks = issueRepository.findSubtasksByAssigneeAndTeam(accountId, m.getTeam().getId());
            for (JiraIssueEntity subtask : subtasks) {
                if (subtask.getDoneAt() != null) continue;

                StatusCategory cat = workflowConfigService.categorize(subtask.getStatus(), subtask.getIssueType());
                if (cat == StatusCategory.IN_PROGRESS || cat == StatusCategory.PLANNED || cat == StatusCategory.DEV_DONE) {
                    activeTasks.add(buildMyTask(subtask, m, issueCache));
                } else if (cat.isNotStarted()) {
                    upcomingAssigned.add(buildMyTask(subtask, m, issueCache));
                }
            }
        }

        Comparator<MyTask> byTeamThenKey = Comparator.comparing(MyTask::teamName).thenComparing(MyTask::key);
        activeTasks.sort(byTeamThenKey);
        upcomingAssigned.sort(byTeamThenKey);

        List<QueueStory> teamQueue = buildTeamQueue(taskMembers, issueCache);

        return new MyWorkResponse(true, memberInfo, upcomingAbsences, activeTasks, upcomingAssigned,
                teamQueue, List.of(), null);
    }

    /**
     * Team queue — nearest board stories that still have unassigned subtasks of the member's phase.
     * For every membership we look at unassigned subtasks in that team, keep only those whose phase
     * (workflowRole, falling back to WorkflowConfigService.getSubtaskRole) matches this membership's
     * role, group them by parent story, drop done parents, order parents like the board
     * (manualOrder asc nulls last → autoScore desc nulls last) and cap the merged result at 10.
     */
    private List<QueueStory> buildTeamQueue(List<TeamMemberEntity> taskMembers, Map<String, JiraIssueEntity> cache) {
        Comparator<JiraIssueEntity> boardOrder = Comparator
                .comparing(JiraIssueEntity::getManualOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(JiraIssueEntity::getAutoScore, Comparator.nullsLast(Comparator.reverseOrder()));

        List<QueueStory> merged = new ArrayList<>();

        for (TeamMemberEntity member : taskMembers) {
            TeamEntity team = member.getTeam();
            String myRole = member.getRole();

            List<JiraIssueEntity> unassigned = issueRepository.findUnassignedSubtasksByTeam(team.getId());

            // Keep only subtasks whose phase matches this membership's role, grouped by parent story.
            Map<String, List<JiraIssueEntity>> byParent = new LinkedHashMap<>();
            for (JiraIssueEntity sub : unassigned) {
                if (sub.getParentKey() == null) continue;
                String phase = sub.getWorkflowRole() != null
                        ? sub.getWorkflowRole()
                        : workflowConfigService.getSubtaskRole(sub.getIssueType());
                if (phase == null || !phase.equals(myRole)) continue;
                byParent.computeIfAbsent(sub.getParentKey(), k -> new ArrayList<>()).add(sub);
            }

            if (byParent.isEmpty()) continue;

            // Resolve parents into cache.
            List<String> missing = byParent.keySet().stream()
                    .filter(k -> !cache.containsKey(k))
                    .toList();
            if (!missing.isEmpty()) {
                for (JiraIssueEntity parent : issueRepository.findByIssueKeyIn(missing)) {
                    cache.put(parent.getIssueKey(), parent);
                }
            }

            // Collect eligible (non-done, resolved) parents, then order like the board.
            List<JiraIssueEntity> parents = new ArrayList<>();
            for (String parentKey : byParent.keySet()) {
                JiraIssueEntity parent = cache.get(parentKey);
                if (parent == null) continue;
                if (workflowConfigService.isDone(parent.getStatus(), parent.getIssueType())) continue;
                parents.add(parent);
            }
            parents.sort(boardOrder);

            for (JiraIssueEntity story : parents) {
                List<JiraIssueEntity> subs = byParent.get(story.getIssueKey());
                long estimateSec = subs.stream()
                        .mapToLong(s -> s.getOriginalEstimateSeconds() != null ? s.getOriginalEstimateSeconds() : 0L)
                        .sum();
                String[] epicInfo = analytics.resolveEpicInfo(subs.get(0), cache);

                merged.add(new QueueStory(
                        story.getIssueKey(),
                        story.getSummary(),
                        story.getIssueType(),
                        story.getStatus(),
                        team.getId(),
                        team.getName(),
                        team.getColor(),
                        epicInfo[0],
                        epicInfo[1],
                        subs.size(),
                        analytics.secondsToHours(estimateSec),
                        jiraConfigResolver.getBaseUrl() + "/browse/" + story.getIssueKey()
                ));
            }
        }

        return merged.stream().limit(10).toList();
    }

    private MyTask buildMyTask(JiraIssueEntity subtask, TeamMemberEntity member, Map<String, JiraIssueEntity> cache) {
        JiraIssueEntity parent = subtask.getParentKey() != null
                ? cache.computeIfAbsent(subtask.getParentKey(), key -> issueRepository.findByIssueKey(key).orElse(null))
                : null;
        String parentSummary = parent != null ? parent.getSummary() : null;

        String[] epicInfo = analytics.resolveEpicInfo(subtask, cache);

        TeamEntity team = member.getTeam();

        return new MyTask(
                subtask.getIssueKey(),
                subtask.getSummary(),
                subtask.getIssueType(),
                subtask.getStatus(),
                subtask.getParentKey(),
                parentSummary,
                epicInfo[0],
                epicInfo[1],
                team.getId(),
                team.getName(),
                team.getColor(),
                analytics.secondsToHours(subtask.getOriginalEstimateSeconds()),
                analytics.secondsToHours(subtask.getTimeSpentSeconds()),
                jiraConfigResolver.getBaseUrl() + "/browse/" + subtask.getIssueKey()
        );
    }
}
