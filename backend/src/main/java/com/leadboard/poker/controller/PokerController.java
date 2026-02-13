package com.leadboard.poker.controller;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.poker.dto.*;
import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.repository.PokerSessionRepository;
import com.leadboard.poker.service.PokerJiraService;
import com.leadboard.poker.service.PokerSessionService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.auth.LeadBoardAuthentication;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/poker")
public class PokerController {

    private static final Logger log = LoggerFactory.getLogger(PokerController.class);

    private final PokerSessionService sessionService;
    private final PokerJiraService jiraService;
    private final JiraIssueRepository issueRepository;
    private final PokerSessionRepository pokerSessionRepository;
    private final WorkflowConfigService workflowConfigService;

    // Statuses of epics eligible for Planning Poker
    private static final List<String> ELIGIBLE_STATUSES = List.of(
            "планирование", "planning",
            "грязная оценка", "rough estimation",
            "в работе", "in progress",
            "запланировано", "planned"
    );

    public PokerController(
            PokerSessionService sessionService,
            PokerJiraService jiraService,
            JiraIssueRepository issueRepository,
            PokerSessionRepository pokerSessionRepository,
            WorkflowConfigService workflowConfigService) {
        this.sessionService = sessionService;
        this.jiraService = jiraService;
        this.issueRepository = issueRepository;
        this.pokerSessionRepository = pokerSessionRepository;
        this.workflowConfigService = workflowConfigService;
    }

    // ===== Eligible Epics =====

    // ===== Existing Stories from Jira =====

    @GetMapping("/epic-stories/{epicKey}")
    public ResponseEntity<List<EpicStoryResponse>> getEpicStories(@PathVariable String epicKey) {
        List<JiraIssueEntity> stories = issueRepository.findByParentKey(epicKey);

        List<EpicStoryResponse> response = stories.stream()
                .filter(s -> workflowConfigService.isStory(s.getIssueType()))
                .map(story -> {
                    // Load subtasks for this story
                    List<JiraIssueEntity> subtasks = issueRepository.findByParentKey(story.getIssueKey());

                    List<String> subtaskRoles = new ArrayList<>();
                    Map<String, Integer> roleEstimates = new HashMap<>();

                    for (JiraIssueEntity subtask : subtasks) {
                        String type = subtask.getIssueType();
                        if (type == null) continue;

                        String roleCode = workflowConfigService.getSubtaskRole(type);
                        if (roleCode != null) {
                            if (!subtaskRoles.contains(roleCode)) {
                                subtaskRoles.add(roleCode);
                            }
                            if (subtask.getOriginalEstimateSeconds() != null) {
                                roleEstimates.put(roleCode, (int) (subtask.getOriginalEstimateSeconds() / 3600));
                            }
                        }
                    }

                    // If no subtasks, assume all roles needed
                    if (subtaskRoles.isEmpty()) {
                        subtaskRoles.addAll(workflowConfigService.getRoleCodesInPipelineOrder());
                    }

                    return new EpicStoryResponse(
                            story.getIssueKey(),
                            story.getSummary(),
                            story.getStatus(),
                            subtaskRoles,
                            roleEstimates
                    );
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/eligible-epics/{teamId}")
    public ResponseEntity<List<EligibleEpicResponse>> getEligibleEpics(@PathVariable Long teamId) {
        // Get epics in eligible statuses
        List<JiraIssueEntity> epics = issueRepository.findEpicsByTeamAndStatuses(teamId, ELIGIBLE_STATUSES);

        // Get epic keys that already have active poker sessions
        Set<String> epicsWithSessions = pokerSessionRepository
                .findByTeamIdAndStatusOrderByCreatedAtDesc(teamId, PokerSessionEntity.SessionStatus.PREPARING)
                .stream()
                .map(PokerSessionEntity::getEpicKey)
                .collect(Collectors.toSet());

        // Also exclude ACTIVE sessions
        epicsWithSessions.addAll(
                pokerSessionRepository
                        .findByTeamIdAndStatusOrderByCreatedAtDesc(teamId, PokerSessionEntity.SessionStatus.ACTIVE)
                        .stream()
                        .map(PokerSessionEntity::getEpicKey)
                        .toList()
        );

        // Map to response
        List<EligibleEpicResponse> response = epics.stream()
                .map(epic -> new EligibleEpicResponse(
                        epic.getIssueKey(),
                        epic.getSummary(),
                        epic.getStatus(),
                        epicsWithSessions.contains(epic.getIssueKey())
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    // ===== Session Endpoints =====

    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request) {

        String facilitatorId = "system";
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof LeadBoardAuthentication lbAuth) {
            facilitatorId = lbAuth.getAtlassianAccountId();
        }

        PokerSessionEntity session = sessionService.createSession(
                request.teamId(),
                request.epicKey(),
                facilitatorId
        );

        return ResponseEntity.ok(SessionResponse.from(session));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable Long id) {
        return sessionService.getSession(id)
                .map(SessionResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions/room/{roomCode}")
    public ResponseEntity<SessionResponse> getSessionByRoomCode(@PathVariable String roomCode) {
        return sessionService.getSessionByRoomCode(roomCode)
                .map(SessionResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions/team/{teamId}")
    public ResponseEntity<List<SessionResponse>> getSessionsByTeam(@PathVariable Long teamId) {
        List<SessionResponse> sessions = sessionService.getSessionsByTeam(teamId).stream()
                .map(SessionResponse::from)
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @PostMapping("/sessions/{id}/start")
    public ResponseEntity<SessionResponse> startSession(@PathVariable Long id) {
        PokerSessionEntity session = sessionService.startSession(id);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    @PostMapping("/sessions/{id}/complete")
    public ResponseEntity<SessionResponse> completeSession(@PathVariable Long id) {
        PokerSessionEntity session = sessionService.completeSession(id);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    // ===== Story Endpoints =====

    @PostMapping("/sessions/{sessionId}/stories")
    public ResponseEntity<StoryResponse> addStory(
            @PathVariable Long sessionId,
            @Valid @RequestBody AddStoryRequest request,
            @RequestParam(defaultValue = "false") boolean createInJira) {

        if (createInJira) {
            // Create in Jira FIRST -- Jira is the single source of truth
            PokerSessionEntity session = sessionService.getSession(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found"));
            String storyKey = jiraService.createStoryInJira(session.getEpicKey(), request.title(), request.needsRoles());
            // Only save to poker session after Jira succeeds
            AddStoryRequest enriched = new AddStoryRequest(request.title(), request.needsRoles(), storyKey);
            PokerStoryEntity story = sessionService.addStory(sessionId, enriched);
            return ResponseEntity.ok(StoryResponse.from(story));
        }

        // Import existing story (already in Jira)
        PokerStoryEntity story = sessionService.addStory(sessionId, request);
        return ResponseEntity.ok(StoryResponse.from(story));
    }

    @DeleteMapping("/stories/{storyId}")
    public ResponseEntity<Void> deleteStory(@PathVariable Long storyId) {
        sessionService.deleteStory(storyId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/{sessionId}/stories")
    public ResponseEntity<List<StoryResponse>> getStories(@PathVariable Long sessionId) {
        List<StoryResponse> stories = sessionService.getStoriesWithVotes(sessionId).stream()
                .map(StoryResponse::from)
                .toList();
        return ResponseEntity.ok(stories);
    }

    // ===== Voting Endpoints (for REST fallback, WebSocket is preferred) =====

    @PostMapping("/stories/{storyId}/reveal")
    public ResponseEntity<StoryResponse> revealVotes(@PathVariable Long storyId) {
        PokerStoryEntity story = sessionService.revealVotes(storyId);
        return ResponseEntity.ok(StoryResponse.from(story));
    }

    @PostMapping("/stories/{storyId}/final")
    public ResponseEntity<StoryResponse> setFinalEstimate(
            @PathVariable Long storyId,
            @RequestBody SetFinalRequest request,
            @RequestParam(defaultValue = "true") boolean updateJira) {

        PokerStoryEntity story = sessionService.setFinalEstimate(storyId, request.finalEstimates());

        if (updateJira && story.getStoryKey() != null) {
            jiraService.updateSubtaskEstimates(story.getStoryKey(), request.finalEstimates());
        }

        return ResponseEntity.ok(StoryResponse.from(story));
    }

    @PostMapping("/sessions/{sessionId}/next")
    public ResponseEntity<StoryResponse> moveToNextStory(@PathVariable Long sessionId) {
        PokerStoryEntity nextStory = sessionService.moveToNextStory(sessionId);
        if (nextStory == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(StoryResponse.from(nextStory));
    }

    @GetMapping("/stories/{storyId}/votes")
    public ResponseEntity<List<VoteResponse>> getVotes(@PathVariable Long storyId) {
        return ResponseEntity.ok(sessionService.getVotes(storyId));
    }
}
