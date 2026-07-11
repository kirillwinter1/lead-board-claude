package com.leadboard.poker.controller;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.jira.JiraClient;
import com.leadboard.status.StatusCategory;
import com.leadboard.poker.dto.*;
import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.PokerStateException;
import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.entity.PokerStoryEntity.StoryStatus;
import com.leadboard.poker.repository.PokerSessionRepository;
import com.leadboard.poker.service.PokerJiraService;
import com.leadboard.poker.service.PokerSessionService;
import com.leadboard.poker.service.PokerSummaryService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.auth.LeadBoardAuthentication;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/poker")
@PreAuthorize("isAuthenticated()")
public class PokerController {

    private static final Logger log = LoggerFactory.getLogger(PokerController.class);

    private final PokerSessionService sessionService;
    private final PokerJiraService jiraService;
    private final PokerSummaryService summaryService;
    private final JiraIssueRepository issueRepository;
    private final PokerSessionRepository pokerSessionRepository;
    private final WorkflowConfigService workflowConfigService;
    private final JiraClient jiraClient;
    private final JiraConfigResolver jiraConfigResolver;

    public PokerController(
            PokerSessionService sessionService,
            PokerJiraService jiraService,
            PokerSummaryService summaryService,
            JiraIssueRepository issueRepository,
            PokerSessionRepository pokerSessionRepository,
            WorkflowConfigService workflowConfigService,
            JiraClient jiraClient,
            JiraConfigResolver jiraConfigResolver) {
        this.sessionService = sessionService;
        this.jiraService = jiraService;
        this.summaryService = summaryService;
        this.issueRepository = issueRepository;
        this.pokerSessionRepository = pokerSessionRepository;
        this.workflowConfigService = workflowConfigService;
        this.jiraClient = jiraClient;
        this.jiraConfigResolver = jiraConfigResolver;
    }

    // ===== Eligible Epics =====

    // ===== Existing Stories from Jira =====

    @GetMapping("/epic-stories/{epicKey}")
    public ResponseEntity<List<EpicStoryResponse>> getEpicStories(@PathVariable String epicKey) {
        List<JiraIssueEntity> stories = issueRepository.findByParentKey(epicKey);

        List<EpicStoryResponse> response = stories.stream()
                .filter(s -> workflowConfigService.isStoryOrBug(s.getIssueType()))
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
        // Planning poker estimates an epic BEFORE development starts, so only epics
        // in a planning-phase category are eligible: NEW / REQUIREMENTS / PLANNED.
        // Epics already in development (IN_PROGRESS / DEV_DONE) or DONE are excluded —
        // their stories are already cut and estimated. Category is config-driven
        // (no hardcoded status names).
        List<JiraIssueEntity> epics = issueRepository.findEpicsByTeam(teamId).stream()
                .filter(e -> isEligibleForPoker(e.getStatus(), e.getIssueType()))
                .toList();

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

    /**
     * Epic is eligible for planning poker only in a planning-phase category.
     * Uses the configured status engine (WorkflowConfigService.categorize, which
     * loads the tenant's status_mappings) — never hardcoded status names or the
     * substring fallback.
     */
    private boolean isEligibleForPoker(String status, String issueType) {
        StatusCategory category = workflowConfigService.categorize(status, issueType).normalized();
        return category == StatusCategory.NEW
                || category == StatusCategory.REQUIREMENTS
                || category == StatusCategory.PLANNED;
    }

    // ===== Session Endpoints =====

    private String currentAccountId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof LeadBoardAuthentication lbAuth) {
            return lbAuth.getAtlassianAccountId();
        }
        return "system";
    }

    /** Mutating session operations are facilitator-only (BUG-176). */
    // Legacy sessions (pre-F89) stored the facilitator as the sentinel "system"; treat any
    // authenticated user as facilitator for those so they aren't permanently locked out.
    private static final String LEGACY_SYSTEM_FACILITATOR = "system";

    private void requireFacilitator(PokerSessionEntity session) {
        String facilitator = session.getFacilitatorAccountId();
        boolean allowed = LEGACY_SYSTEM_FACILITATOR.equals(facilitator)
                || currentAccountId().equals(facilitator);
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the session facilitator can perform this action");
        }
    }

    private PokerSessionEntity getSessionOrThrow(Long sessionId) {
        return sessionService.getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request) {

        PokerSessionEntity session = sessionService.createSession(
                request.teamId(),
                request.epicKey(),
                currentAccountId()
        );

        return ResponseEntity.ok(SessionResponse.from(session));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<SessionResponse> getSession(@PathVariable Long id) {
        return sessionService.getSession(id)
                .map(this::withEpicDetails)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sessions/room/{roomCode}")
    public ResponseEntity<SessionResponse> getSessionByRoomCode(@PathVariable String roomCode) {
        return sessionService.getSessionByRoomCode(roomCode)
                .map(this::withEpicDetails)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * F23 rework: rooms are addressed by epic key, not room code. Returns the active
     * (PREPARING or ACTIVE) session for the epic; if none is active, falls back to the
     * most recent session (e.g. a COMPLETED one, so its results stay viewable).
     * Enriched with epicSummary/epicDescription. 404 when the epic has no session at all.
     */
    @GetMapping("/sessions/epic/{epicKey}")
    public ResponseEntity<SessionResponse> getActiveSessionByEpic(@PathVariable String epicKey) {
        Optional<PokerSessionEntity> active = pokerSessionRepository.findByEpicKeyAndStatusInWithStories(
                epicKey,
                List.of(PokerSessionEntity.SessionStatus.PREPARING, PokerSessionEntity.SessionStatus.ACTIVE))
                .stream().findFirst();

        // Fall back to the most recent session of any status (view completed results).
        Optional<PokerSessionEntity> session = active.or(() ->
                pokerSessionRepository.findByEpicKeyOrderByCreatedAtDesc(epicKey).stream()
                        .findFirst()
                        .flatMap(s -> sessionService.getSession(s.getId())));

        return session
                .map(this::withEpicDetails)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ===== Jira Project Components (for the "Add story" form) =====

    /** Components for the default project (JiraConfigResolver.getProjectKey()). */
    @GetMapping("/projects/components")
    public ResponseEntity<List<ComponentResponse>> getDefaultProjectComponents() {
        return ResponseEntity.ok(fetchComponents(jiraConfigResolver.getProjectKey()));
    }

    /** Components for a specific Jira project. */
    @GetMapping("/projects/{projectKey}/components")
    public ResponseEntity<List<ComponentResponse>> getProjectComponents(@PathVariable String projectKey) {
        return ResponseEntity.ok(fetchComponents(projectKey));
    }

    private List<ComponentResponse> fetchComponents(String projectKey) {
        return jiraClient.getComponents(projectKey).stream()
                .map(c -> new ComponentResponse(c.get("id"), c.get("name")))
                .toList();
    }

    /** Builds a SessionResponse enriched with the epic's summary and description. */
    private SessionResponse withEpicDetails(PokerSessionEntity session) {
        return issueRepository.findByIssueKey(session.getEpicKey())
                .map(epic -> SessionResponse.from(session, epic.getSummary(), epic.getDescription()))
                .orElseGet(() -> SessionResponse.from(session));
    }

    @GetMapping("/sessions/team/{teamId}")
    public ResponseEntity<List<SessionResponse>> getSessionsByTeam(@PathVariable Long teamId) {
        List<SessionResponse> sessions = sessionService.getSessionsByTeam(teamId).stream()
                .map(this::withEpicDetails)
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @PostMapping("/sessions/{id}/start")
    public ResponseEntity<SessionResponse> startSession(@PathVariable Long id) {
        requireFacilitator(getSessionOrThrow(id));
        PokerSessionEntity session = sessionService.startSession(id);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    @PostMapping("/sessions/{id}/complete")
    public ResponseEntity<SessionResponse> completeSession(@PathVariable Long id) {
        requireFacilitator(getSessionOrThrow(id));
        PokerSessionEntity session = sessionService.completeSession(id);
        return ResponseEntity.ok(SessionResponse.from(session));
    }

    /**
     * F23 rework: publish final estimates to Jira. Facilitator-only. For each COMPLETED
     * story with final estimates, ensures a subtask per role and writes the role's
     * Original Estimate. Idempotent — safe to re-run. Returns per-story status.
     */
    @PostMapping("/sessions/{id}/publish")
    public ResponseEntity<PublishResultResponse> publishSession(@PathVariable Long id) {
        requireFacilitator(getSessionOrThrow(id));
        return ResponseEntity.ok(jiraService.publishSession(id));
    }

    /**
     * F23 rework: session summary — stories with final estimates plus a rough-vs-poker
     * comparison by role and the resulting planning error.
     */
    @GetMapping("/sessions/{id}/summary")
    public ResponseEntity<SessionSummaryResponse> getSummary(@PathVariable Long id) {
        return ResponseEntity.ok(summaryService.buildSummary(id));
    }

    // ===== Story Endpoints =====

    @PostMapping("/sessions/{sessionId}/stories")
    public ResponseEntity<StoryResponse> addStory(
            @PathVariable Long sessionId,
            @Valid @RequestBody AddStoryRequest request,
            @RequestParam(defaultValue = "false") boolean createInJira) {

        PokerSessionEntity session = getSessionOrThrow(sessionId);
        requireFacilitator(session);

        if (createInJira) {
            // Create in Jira FIRST -- Jira is the single source of truth
            String storyKey = jiraService.createStoryInJira(
                    session.getEpicKey(), request.title(), request.description(),
                    request.component(), request.needsRoles());
            // Only save to poker session after Jira succeeds
            AddStoryRequest enriched = new AddStoryRequest(
                    request.title(), request.needsRoles(), request.description(),
                    request.component(), storyKey);
            PokerStoryEntity story = sessionService.addStory(sessionId, enriched);
            return ResponseEntity.ok(StoryResponse.from(story));
        }

        // Import existing story (already in Jira)
        PokerStoryEntity story = sessionService.addStory(sessionId, request);
        return ResponseEntity.ok(StoryResponse.from(story));
    }

    @PutMapping("/stories/{storyId}")
    public ResponseEntity<StoryResponse> updateStory(
            @PathVariable Long storyId,
            @Valid @RequestBody AddStoryRequest request) {
        PokerStoryEntity existing = sessionService.getStoryWithSession(storyId);
        requireFacilitator(existing.getSession());

        // Validate editability BEFORE touching Jira: only a not-yet-estimated story may be
        // edited. Writing to Jira first and letting the local update throw afterwards would
        // leave Jira and the DB diverging (BUG: non-PENDING edit). Same guard as
        // PokerSessionService.updateStory, checked here so no Jira write happens on reject.
        if (existing.getStatus() != StoryStatus.PENDING) {
            throw new PokerStateException("Only a not-yet-estimated story can be edited");
        }

        // Keep Jira in sync (single source of truth) when the story is already there.
        if (existing.getStoryKey() != null && !existing.getStoryKey().isBlank()) {
            jiraService.updateStoryInJira(existing.getStoryKey(), request.title(), request.description());
        }
        PokerStoryEntity story = sessionService.updateStory(storyId, request);
        return ResponseEntity.ok(StoryResponse.from(story));
    }

    @DeleteMapping("/stories/{storyId}")
    public ResponseEntity<Void> deleteStory(@PathVariable Long storyId) {
        requireFacilitator(sessionService.getStoryWithSession(storyId).getSession());
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
        requireFacilitator(sessionService.getStoryWithSession(storyId).getSession());
        PokerStoryEntity story = sessionService.revealVotes(storyId);
        return ResponseEntity.ok(StoryResponse.from(story));
    }

    @PostMapping("/stories/{storyId}/final")
    public ResponseEntity<StoryResponse> setFinalEstimate(
            @PathVariable Long storyId,
            @RequestBody SetFinalRequest request,
            @RequestParam(defaultValue = "true") boolean updateJira) {

        requireFacilitator(sessionService.getStoryWithSession(storyId).getSession());
        PokerStoryEntity story = sessionService.setFinalEstimate(storyId, request.finalEstimates());

        if (updateJira && story.getStoryKey() != null) {
            jiraService.updateSubtaskEstimates(story.getStoryKey(), request.finalEstimates());
        }

        return ResponseEntity.ok(StoryResponse.from(story));
    }

    @PostMapping("/sessions/{sessionId}/next")
    public ResponseEntity<StoryResponse> moveToNextStory(@PathVariable Long sessionId) {
        requireFacilitator(getSessionOrThrow(sessionId));
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
