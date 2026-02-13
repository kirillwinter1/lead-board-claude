package com.leadboard.poker.service;

import com.leadboard.poker.dto.AddStoryRequest;
import com.leadboard.poker.dto.ParticipantInfo;
import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.entity.PokerSessionEntity.SessionStatus;
import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.entity.PokerStoryEntity.StoryStatus;
import com.leadboard.poker.entity.PokerVoteEntity;
import com.leadboard.poker.repository.PokerSessionRepository;
import com.leadboard.poker.repository.PokerStoryRepository;
import com.leadboard.poker.repository.PokerVoteRepository;
import com.leadboard.team.TeamMemberEntity;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.team.TeamMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PokerSessionServiceTest {

    @Mock
    private PokerSessionRepository sessionRepository;

    @Mock
    private PokerStoryRepository storyRepository;

    @Mock
    private PokerVoteRepository voteRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private WorkflowConfigService workflowConfigService;

    private PokerSessionService pokerSessionService;

    @BeforeEach
    void setUp() {
        pokerSessionService = new PokerSessionService(
                sessionRepository,
                storyRepository,
                voteRepository,
                teamMemberRepository,
                workflowConfigService
        );
    }

    // ==================== createSession() Tests ====================

    @Nested
    @DisplayName("createSession()")
    class CreateSessionTests {

        @Test
        @DisplayName("should create new session")
        void shouldCreateNewSession() {
            when(sessionRepository.existsByRoomCode(any())).thenReturn(false);
            when(sessionRepository.save(any(PokerSessionEntity.class))).thenAnswer(i -> {
                PokerSessionEntity session = i.getArgument(0);
                session.setId(1L);
                return session;
            });

            PokerSessionEntity session = pokerSessionService.createSession(1L, "LB-100", "facilitator-123");

            assertNotNull(session);
            assertEquals(1L, session.getTeamId());
            assertEquals("LB-100", session.getEpicKey());
            assertEquals("facilitator-123", session.getFacilitatorAccountId());
            assertEquals(SessionStatus.PREPARING, session.getStatus());
        }

        @Test
        @DisplayName("should generate unique room code")
        void shouldGenerateUniqueCode() {
            when(sessionRepository.existsByRoomCode(any())).thenReturn(false);
            when(sessionRepository.save(any(PokerSessionEntity.class))).thenAnswer(i -> i.getArgument(0));

            PokerSessionEntity session = pokerSessionService.createSession(1L, "LB-100", "facilitator");

            assertNotNull(session.getRoomCode());
            assertEquals(6, session.getRoomCode().length());
        }

        @Test
        @DisplayName("should set initial state to PREPARING")
        void shouldSetInitialState() {
            when(sessionRepository.existsByRoomCode(any())).thenReturn(false);
            when(sessionRepository.save(any(PokerSessionEntity.class))).thenAnswer(i -> i.getArgument(0));

            PokerSessionEntity session = pokerSessionService.createSession(1L, "LB-100", "facilitator");

            assertEquals(SessionStatus.PREPARING, session.getStatus());
        }
    }

    // ==================== startSession() Tests ====================

    @Nested
    @DisplayName("startSession()")
    class StartSessionTests {

        @Test
        @DisplayName("should start session and set first story to VOTING")
        void shouldStartSessionAndSetFirstStoryToVoting() {
            PokerSessionEntity session = createSession(1L, SessionStatus.PREPARING);
            PokerStoryEntity story = createStory(1L, session, "Story 1", 0);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(storyRepository.findBySessionIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(story));
            when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(storyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PokerSessionEntity started = pokerSessionService.startSession(1L);

            assertEquals(SessionStatus.ACTIVE, started.getStatus());
            assertNotNull(started.getStartedAt());
            assertEquals(StoryStatus.VOTING, story.getStatus());
        }

        @Test
        @DisplayName("should throw exception if session not in PREPARING state")
        void shouldThrowIfNotPreparing() {
            PokerSessionEntity session = createSession(1L, SessionStatus.ACTIVE);
            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

            assertThrows(IllegalStateException.class, () -> pokerSessionService.startSession(1L));
        }

        @Test
        @DisplayName("should throw exception if session not found")
        void shouldThrowIfNotFound() {
            when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> pokerSessionService.startSession(999L));
        }
    }

    // ==================== castVote() Tests ====================

    @Nested
    @DisplayName("castVote()")
    class CastVoteTests {

        @Test
        @DisplayName("should record vote")
        void shouldRecordVote() {
            PokerStoryEntity story = createStory(1L, null, "Story", 0);
            story.setStatus(StoryStatus.VOTING);

            when(storyRepository.findById(1L)).thenReturn(Optional.of(story));
            when(voteRepository.findByStoryIdAndVoterAccountIdAndVoterRole(1L, "voter-123", "DEV"))
                    .thenReturn(Optional.empty());
            when(voteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PokerVoteEntity vote = pokerSessionService.castVote(1L, "voter-123", "John", "DEV", 8);

            assertEquals("voter-123", vote.getVoterAccountId());
            assertEquals("John", vote.getVoterDisplayName());
            assertEquals("DEV", vote.getVoterRole());
            assertEquals(8, vote.getVoteHours());
        }

        @Test
        @DisplayName("should update on revote")
        void shouldUpdateOnRevote() {
            PokerStoryEntity story = createStory(1L, null, "Story", 0);
            story.setStatus(StoryStatus.VOTING);

            PokerVoteEntity existingVote = new PokerVoteEntity();
            existingVote.setStory(story);
            existingVote.setVoterAccountId("voter-123");
            existingVote.setVoterRole("DEV");
            existingVote.setVoteHours(4);

            when(storyRepository.findById(1L)).thenReturn(Optional.of(story));
            when(voteRepository.findByStoryIdAndVoterAccountIdAndVoterRole(1L, "voter-123", "DEV"))
                    .thenReturn(Optional.of(existingVote));
            when(voteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PokerVoteEntity vote = pokerSessionService.castVote(1L, "voter-123", "John", "DEV", 16);

            assertEquals(16, vote.getVoteHours());
        }

        @Test
        @DisplayName("should reject vote after reveal")
        void shouldRejectAfterReveal() {
            PokerStoryEntity story = createStory(1L, null, "Story", 0);
            story.setStatus(StoryStatus.REVEALED);

            when(storyRepository.findById(1L)).thenReturn(Optional.of(story));

            assertThrows(IllegalStateException.class, () ->
                    pokerSessionService.castVote(1L, "voter", "John", "DEV", 8));
        }

        @Test
        @DisplayName("should reject vote for unneeded role")
        void shouldRejectUnneededRole() {
            PokerStoryEntity story = createStory(1L, null, "Story", 0);
            story.setStatus(StoryStatus.VOTING);
            story.setNeedsRoles(List.of("DEV"));

            when(storyRepository.findById(1L)).thenReturn(Optional.of(story));

            assertThrows(IllegalArgumentException.class, () ->
                    pokerSessionService.castVote(1L, "voter", "John", "SA", 4));
        }
    }

    // ==================== revealVotes() Tests ====================

    @Nested
    @DisplayName("revealVotes()")
    class RevealVotesTests {

        @Test
        @DisplayName("should reveal all votes")
        void shouldRevealAllVotes() {
            PokerStoryEntity story = createStory(1L, null, "Story", 0);
            story.setStatus(StoryStatus.VOTING);

            when(storyRepository.findById(1L)).thenReturn(Optional.of(story));
            when(storyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PokerStoryEntity revealed = pokerSessionService.revealVotes(1L);

            assertEquals(StoryStatus.REVEALED, revealed.getStatus());
        }

        @Test
        @DisplayName("should throw if not in VOTING state")
        void shouldThrowIfNotVoting() {
            PokerStoryEntity story = createStory(1L, null, "Story", 0);
            story.setStatus(StoryStatus.COMPLETED);

            when(storyRepository.findById(1L)).thenReturn(Optional.of(story));

            assertThrows(IllegalStateException.class, () -> pokerSessionService.revealVotes(1L));
        }
    }

    // ==================== setFinalEstimate() Tests ====================

    @Nested
    @DisplayName("setFinalEstimate()")
    class SetFinalEstimateTests {

        @Test
        @DisplayName("should set final estimates and complete story")
        void shouldSetFinalEstimates() {
            PokerStoryEntity story = createStory(1L, null, "Story", 0);
            story.setStatus(StoryStatus.REVEALED);

            when(storyRepository.findById(1L)).thenReturn(Optional.of(story));
            when(storyRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Map<String, Integer> estimates = Map.of("SA", 4, "DEV", 16, "QA", 8);
            PokerStoryEntity completed = pokerSessionService.setFinalEstimate(1L, estimates);

            assertEquals(4, completed.getFinalEstimate("SA"));
            assertEquals(16, completed.getFinalEstimate("DEV"));
            assertEquals(8, completed.getFinalEstimate("QA"));
            assertEquals(StoryStatus.COMPLETED, completed.getStatus());
        }
    }

    // ==================== completeSession() Tests ====================

    @Nested
    @DisplayName("completeSession()")
    class CompleteSessionTests {

        @Test
        @DisplayName("should mark session as closed")
        void shouldMarkAsClosed() {
            PokerSessionEntity session = createSession(1L, SessionStatus.ACTIVE);

            when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            PokerSessionEntity completed = pokerSessionService.completeSession(1L);

            assertEquals(SessionStatus.COMPLETED, completed.getStatus());
            assertNotNull(completed.getCompletedAt());
        }
    }

    // ==================== Participant Management Tests ====================

    @Nested
    @DisplayName("Participant management")
    class ParticipantTests {

        @Test
        @DisplayName("should add participant")
        void shouldAddParticipant() {
            ParticipantInfo participant = new ParticipantInfo("account-1", "John", "DEV", false, true);

            pokerSessionService.addParticipant("ROOM01", participant);
            List<ParticipantInfo> participants = pokerSessionService.getParticipants("ROOM01");

            assertEquals(1, participants.size());
            assertEquals("John", participants.get(0).displayName());
        }

        @Test
        @DisplayName("should allow rejoin")
        void shouldAllowRejoin() {
            ParticipantInfo participant1 = new ParticipantInfo("account-1", "John", "DEV", false, true);
            ParticipantInfo participant2 = new ParticipantInfo("account-1", "John Updated", "DEV", false, true);

            pokerSessionService.addParticipant("ROOM01", participant1);
            pokerSessionService.addParticipant("ROOM01", participant2);

            List<ParticipantInfo> participants = pokerSessionService.getParticipants("ROOM01");

            assertEquals(1, participants.size());
            assertEquals("John Updated", participants.get(0).displayName());
        }

        @Test
        @DisplayName("should return empty list for invalid room code")
        void shouldReturnEmptyForInvalidCode() {
            List<ParticipantInfo> participants = pokerSessionService.getParticipants("INVALID");

            assertTrue(participants.isEmpty());
        }
    }

    // ==================== getParticipantRole() Tests ====================

    @Nested
    @DisplayName("getParticipantRole()")
    class GetParticipantRoleTests {

        @Test
        @DisplayName("should map team member role to string")
        void shouldMapRoleToString() {
            TeamMemberEntity member = new TeamMemberEntity();
            member.setRole("DEV");

            when(teamMemberRepository.findByTeamIdAndJiraAccountId(1L, "account-1"))
                    .thenReturn(Optional.of(member));

            Optional<String> role = pokerSessionService.getParticipantRole(1L, "account-1");

            assertTrue(role.isPresent());
            assertEquals("DEV", role.get());
        }

        @Test
        @DisplayName("should return empty for unknown participant")
        void shouldReturnEmptyForUnknown() {
            when(teamMemberRepository.findByTeamIdAndJiraAccountId(1L, "unknown"))
                    .thenReturn(Optional.empty());

            Optional<String> role = pokerSessionService.getParticipantRole(1L, "unknown");

            assertTrue(role.isEmpty());
        }
    }

    // ==================== Helper Methods ====================

    private PokerSessionEntity createSession(Long id, SessionStatus status) {
        PokerSessionEntity session = new PokerSessionEntity();
        session.setId(id);
        session.setTeamId(1L);
        session.setEpicKey("LB-100");
        session.setRoomCode("ROOM01");
        session.setStatus(status);
        return session;
    }

    private PokerStoryEntity createStory(Long id, PokerSessionEntity session, String title, int orderIndex) {
        PokerStoryEntity story = new PokerStoryEntity();
        story.setId(id);
        story.setSession(session);
        story.setTitle(title);
        story.setOrderIndex(orderIndex);
        story.setStatus(StoryStatus.PENDING);
        story.setNeedsRoles(List.of("SA", "DEV", "QA"));
        return story;
    }
}
