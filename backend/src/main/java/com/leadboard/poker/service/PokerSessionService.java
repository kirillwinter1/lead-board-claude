package com.leadboard.poker.service;

import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.poker.dto.*;
import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.entity.PokerSessionEntity.SessionStatus;
import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.entity.PokerStoryEntity.StoryStatus;
import com.leadboard.poker.entity.PokerVoteEntity;
import com.leadboard.poker.repository.PokerSessionRepository;
import com.leadboard.poker.repository.PokerStoryRepository;
import com.leadboard.poker.repository.PokerVoteRepository;
import com.leadboard.team.TeamMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PokerSessionService {

    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int ROOM_CODE_LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();

    private final PokerSessionRepository sessionRepository;
    private final PokerStoryRepository storyRepository;
    private final PokerVoteRepository voteRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final WorkflowConfigService workflowConfigService;

    // In-memory tracking of online participants per room
    private final Map<String, Map<String, ParticipantInfo>> roomParticipants = new ConcurrentHashMap<>();

    public PokerSessionService(
            PokerSessionRepository sessionRepository,
            PokerStoryRepository storyRepository,
            PokerVoteRepository voteRepository,
            TeamMemberRepository teamMemberRepository,
            WorkflowConfigService workflowConfigService) {
        this.sessionRepository = sessionRepository;
        this.storyRepository = storyRepository;
        this.voteRepository = voteRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.workflowConfigService = workflowConfigService;
    }

    // ===== Session Management =====

    @Transactional
    public PokerSessionEntity createSession(Long teamId, String epicKey, String facilitatorAccountId) {
        PokerSessionEntity session = new PokerSessionEntity();
        session.setTeamId(teamId);
        session.setEpicKey(epicKey);
        session.setFacilitatorAccountId(facilitatorAccountId);
        session.setRoomCode(generateUniqueRoomCode());
        session.setStatus(SessionStatus.PREPARING);
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public Optional<PokerSessionEntity> getSession(Long id) {
        return sessionRepository.findByIdWithStories(id);
    }

    @Transactional(readOnly = true)
    public Optional<PokerSessionEntity> getSessionByRoomCode(String roomCode) {
        return sessionRepository.findByRoomCodeWithStories(roomCode);
    }

    @Transactional(readOnly = true)
    public List<PokerSessionEntity> getSessionsByTeam(Long teamId) {
        return sessionRepository.findByTeamIdOrderByCreatedAtDesc(teamId);
    }

    @Transactional(readOnly = true)
    public List<PokerSessionEntity> getActiveSessionsByTeam(Long teamId) {
        return sessionRepository.findByTeamIdAndStatusOrderByCreatedAtDesc(teamId, SessionStatus.ACTIVE);
    }

    @Transactional
    public PokerSessionEntity startSession(Long sessionId) {
        PokerSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        if (session.getStatus() != SessionStatus.PREPARING) {
            throw new IllegalStateException("Session is not in PREPARING state");
        }

        session.setStatus(SessionStatus.ACTIVE);
        session.setStartedAt(OffsetDateTime.now());

        // Set first story to VOTING
        List<PokerStoryEntity> stories = storyRepository.findBySessionIdOrderByOrderIndexAsc(sessionId);
        if (!stories.isEmpty()) {
            PokerStoryEntity firstStory = stories.get(0);
            firstStory.setStatus(StoryStatus.VOTING);
            storyRepository.save(firstStory);
        }

        return sessionRepository.save(session);
    }

    @Transactional
    public PokerSessionEntity completeSession(Long sessionId) {
        PokerSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        session.setStatus(SessionStatus.COMPLETED);
        session.setCompletedAt(OffsetDateTime.now());
        return sessionRepository.save(session);
    }

    // ===== Story Management =====

    @Transactional
    public PokerStoryEntity addStory(Long sessionId, AddStoryRequest request) {
        PokerSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        Integer maxOrder = storyRepository.findMaxOrderIndexBySessionId(sessionId).orElse(-1);

        PokerStoryEntity story = new PokerStoryEntity();
        story.setSession(session);
        story.setTitle(request.title());
        story.setStoryKey(request.existingStoryKey());
        story.setNeedsRoles(request.needsRoles());
        story.setOrderIndex(maxOrder + 1);
        story.setStatus(StoryStatus.PENDING);

        return storyRepository.save(story);
    }

    @Transactional
    public void updateStoryJiraKey(Long storyId, String jiraKey) {
        PokerStoryEntity story = storyRepository.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Story not found"));
        story.setStoryKey(jiraKey);
        storyRepository.save(story);
    }

    @Transactional
    public void deleteStory(Long storyId) {
        storyRepository.deleteById(storyId);
    }

    @Transactional(readOnly = true)
    public List<PokerStoryEntity> getStoriesWithVotes(Long sessionId) {
        return storyRepository.findBySessionIdWithVotes(sessionId);
    }

    // ===== Voting =====

    @Transactional
    public PokerVoteEntity castVote(Long storyId, String voterAccountId, String voterDisplayName, String role, Integer hours) {
        PokerStoryEntity story = storyRepository.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Story not found"));

        if (story.getStatus() != StoryStatus.VOTING) {
            throw new IllegalStateException("Voting is not active for this story");
        }

        // Check if role is needed for this story
        if (!story.needsRole(role)) {
            throw new IllegalArgumentException("This role is not needed for this story");
        }

        // Find or create vote
        PokerVoteEntity vote = voteRepository.findByStoryIdAndVoterAccountIdAndVoterRole(storyId, voterAccountId, role)
                .orElseGet(() -> {
                    PokerVoteEntity newVote = new PokerVoteEntity();
                    newVote.setStory(story);
                    newVote.setVoterAccountId(voterAccountId);
                    newVote.setVoterRole(role);
                    return newVote;
                });

        vote.setVoterDisplayName(voterDisplayName);
        vote.setVoteHours(hours);
        vote.setVotedAt(OffsetDateTime.now());

        return voteRepository.save(vote);
    }

    @Transactional
    public PokerStoryEntity revealVotes(Long storyId) {
        PokerStoryEntity story = storyRepository.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Story not found"));

        if (story.getStatus() != StoryStatus.VOTING) {
            throw new IllegalStateException("Story is not in VOTING state");
        }

        story.setStatus(StoryStatus.REVEALED);
        return storyRepository.save(story);
    }

    @Transactional
    public PokerStoryEntity setFinalEstimate(Long storyId, Map<String, Integer> finalEstimates) {
        PokerStoryEntity story = storyRepository.findById(storyId)
                .orElseThrow(() -> new IllegalArgumentException("Story not found"));

        story.setFinalEstimates(finalEstimates);
        story.setStatus(StoryStatus.COMPLETED);

        return storyRepository.save(story);
    }

    @Transactional
    public PokerStoryEntity moveToNextStory(Long sessionId) {
        List<PokerStoryEntity> pendingStories = storyRepository.findPendingStoriesBySessionId(sessionId);

        if (pendingStories.isEmpty()) {
            return null; // No more stories
        }

        PokerStoryEntity nextStory = pendingStories.get(0);
        nextStory.setStatus(StoryStatus.VOTING);
        return storyRepository.save(nextStory);
    }

    @Transactional(readOnly = true)
    public List<VoteResponse> getVotes(Long storyId) {
        return voteRepository.findByStoryId(storyId).stream()
                .map(VoteResponse::from)
                .toList();
    }

    // ===== Participant Management =====

    public void addParticipant(String roomCode, ParticipantInfo participant) {
        roomParticipants.computeIfAbsent(roomCode, k -> new ConcurrentHashMap<>())
                .put(participant.accountId(), participant);
    }

    public void removeParticipant(String roomCode, String accountId) {
        Map<String, ParticipantInfo> participants = roomParticipants.get(roomCode);
        if (participants != null) {
            participants.remove(accountId);
        }
    }

    public List<ParticipantInfo> getParticipants(String roomCode) {
        Map<String, ParticipantInfo> participants = roomParticipants.get(roomCode);
        return participants != null ? List.copyOf(participants.values()) : List.of();
    }

    public Optional<String> getParticipantRole(Long teamId, String accountId) {
        return teamMemberRepository.findByTeamIdAndJiraAccountId(teamId, accountId)
                .map(member -> member.getRole());
    }

    // ===== Session State =====

    @Transactional(readOnly = true)
    public SessionState buildSessionState(String roomCode) {
        PokerSessionEntity session = sessionRepository.findByRoomCodeWithStories(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        List<PokerStoryEntity> stories = storyRepository.findBySessionIdWithVotes(session.getId());

        Long currentStoryId = stories.stream()
                .filter(s -> s.getStatus() == StoryStatus.VOTING)
                .findFirst()
                .map(PokerStoryEntity::getId)
                .orElse(null);

        // Map stories based on their status (hide votes if not revealed)
        List<StoryResponse> storyResponses = stories.stream()
                .map(story -> story.getStatus() == StoryStatus.VOTING
                        ? StoryResponse.fromWithoutVoteValues(story)
                        : StoryResponse.from(story))
                .toList();

        return new SessionState(
                session.getId(),
                session.getTeamId(),
                session.getEpicKey(),
                session.getStatus().name(),
                session.getRoomCode(),
                session.getFacilitatorAccountId(),
                storyResponses,
                currentStoryId,
                getParticipants(roomCode)
        );
    }

    // ===== Helpers =====

    private String generateUniqueRoomCode() {
        String code;
        do {
            code = generateRoomCode();
        } while (sessionRepository.existsByRoomCode(code));
        return code;
    }

    private String generateRoomCode() {
        StringBuilder sb = new StringBuilder(ROOM_CODE_LENGTH);
        for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
            sb.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
