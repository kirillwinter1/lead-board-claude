package com.leadboard.poker.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadboard.poker.dto.*;
import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.entity.PokerVoteEntity.VoterRole;
import com.leadboard.poker.service.PokerJiraService;
import com.leadboard.poker.service.PokerSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class PokerWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PokerWebSocketHandler.class);
    private static final UriTemplate URI_TEMPLATE = new UriTemplate("/ws/poker/{roomCode}");

    private final ObjectMapper objectMapper;
    private final PokerSessionService sessionService;
    private final PokerJiraService jiraService;

    // Room code -> list of sessions in that room
    private final Map<String, CopyOnWriteArrayList<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    // Session ID -> participant info
    private final Map<String, ParticipantInfo> sessionParticipants = new ConcurrentHashMap<>();

    public PokerWebSocketHandler(ObjectMapper objectMapper, PokerSessionService sessionService, PokerJiraService jiraService) {
        this.objectMapper = objectMapper;
        this.sessionService = sessionService;
        this.jiraService = jiraService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomCode = extractRoomCode(session);
        if (roomCode == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        log.info("WebSocket connection established for room: {}", roomCode);
        roomSessions.computeIfAbsent(roomCode, k -> new CopyOnWriteArrayList<>()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomCode = extractRoomCode(session);
        if (roomCode != null) {
            CopyOnWriteArrayList<WebSocketSession> sessions = roomSessions.get(roomCode);
            if (sessions != null) {
                sessions.remove(session);
            }

            ParticipantInfo participant = sessionParticipants.remove(session.getId());
            if (participant != null) {
                sessionService.removeParticipant(roomCode, participant.accountId());
                broadcastToRoom(roomCode, PokerMessage.participantLeft(participant.accountId()));
            }
        }
        log.info("WebSocket connection closed: {}", status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomCode = extractRoomCode(session);
        if (roomCode == null) return;

        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            switch (type) {
                case PokerMessage.TYPE_JOIN -> handleJoin(session, roomCode, payload);
                case PokerMessage.TYPE_VOTE -> handleVote(session, roomCode, payload);
                case PokerMessage.TYPE_REVEAL -> handleReveal(session, roomCode, payload);
                case PokerMessage.TYPE_SET_FINAL -> handleSetFinal(session, roomCode, payload);
                case PokerMessage.TYPE_NEXT_STORY -> handleNextStory(session, roomCode, payload);
                case PokerMessage.TYPE_START_SESSION -> handleStartSession(session, roomCode, payload);
                case PokerMessage.TYPE_COMPLETE_SESSION -> handleCompleteSession(session, roomCode, payload);
                default -> sendError(session, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            sendError(session, "Error: " + e.getMessage());
        }
    }

    private void handleJoin(WebSocketSession session, String roomCode, Map<String, Object> payload) throws IOException {
        String accountId = (String) payload.get("accountId");
        String displayName = (String) payload.get("displayName");
        String role = (String) payload.get("role");
        boolean isFacilitator = Boolean.TRUE.equals(payload.get("isFacilitator"));

        ParticipantInfo participant = new ParticipantInfo(
                accountId,
                displayName,
                role,
                isFacilitator,
                true
        );

        sessionParticipants.put(session.getId(), participant);
        sessionService.addParticipant(roomCode, participant);

        // Send current state to the joining participant
        SessionState state = sessionService.buildSessionState(roomCode);
        sendMessage(session, PokerMessage.state(state));

        // Notify others about the new participant
        broadcastToRoom(roomCode, PokerMessage.participantJoined(participant), session);
    }

    private void handleVote(WebSocketSession session, String roomCode, Map<String, Object> payload) throws IOException {
        Long storyId = ((Number) payload.get("storyId")).longValue();
        Integer hours = payload.get("hours") != null ? ((Number) payload.get("hours")).intValue() : null;

        ParticipantInfo participant = sessionParticipants.get(session.getId());
        if (participant == null) {
            sendError(session, "Not joined to room");
            return;
        }

        VoterRole voterRole = VoterRole.valueOf(participant.role());
        sessionService.castVote(storyId, participant.accountId(), participant.displayName(), voterRole, hours);

        // Broadcast that a vote was cast (without revealing the value)
        broadcastToRoom(roomCode, PokerMessage.voteCast(storyId, participant.accountId(), participant.role()));
    }

    private void handleReveal(WebSocketSession session, String roomCode, Map<String, Object> payload) throws IOException {
        Long storyId = ((Number) payload.get("storyId")).longValue();

        // Check if facilitator
        ParticipantInfo participant = sessionParticipants.get(session.getId());
        if (participant == null || !participant.isFacilitator()) {
            sendError(session, "Only facilitator can reveal votes");
            return;
        }

        sessionService.revealVotes(storyId);
        List<VoteResponse> votes = sessionService.getVotes(storyId);

        broadcastToRoom(roomCode, PokerMessage.votesRevealed(storyId, votes));
    }

    private void handleSetFinal(WebSocketSession session, String roomCode, Map<String, Object> payload) throws IOException {
        Long storyId = ((Number) payload.get("storyId")).longValue();
        Integer saHours = payload.get("saHours") != null ? ((Number) payload.get("saHours")).intValue() : null;
        Integer devHours = payload.get("devHours") != null ? ((Number) payload.get("devHours")).intValue() : null;
        Integer qaHours = payload.get("qaHours") != null ? ((Number) payload.get("qaHours")).intValue() : null;

        // Check if facilitator
        ParticipantInfo participant = sessionParticipants.get(session.getId());
        if (participant == null || !participant.isFacilitator()) {
            sendError(session, "Only facilitator can set final estimate");
            return;
        }

        PokerStoryEntity story = sessionService.setFinalEstimate(storyId, saHours, devHours, qaHours);

        // Update Jira if story has a key
        if (story.getStoryKey() != null) {
            try {
                jiraService.updateSubtaskEstimates(story.getStoryKey(), saHours, devHours, qaHours);
            } catch (Exception e) {
                log.error("Failed to update Jira estimates: {}", e.getMessage());
            }
        }

        broadcastToRoom(roomCode, PokerMessage.storyCompleted(storyId, saHours, devHours, qaHours));
    }

    private void handleNextStory(WebSocketSession session, String roomCode, Map<String, Object> payload) throws IOException {
        // Check if facilitator
        ParticipantInfo participant = sessionParticipants.get(session.getId());
        if (participant == null || !participant.isFacilitator()) {
            sendError(session, "Only facilitator can move to next story");
            return;
        }

        PokerSessionEntity pokerSession = sessionService.getSessionByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        PokerStoryEntity nextStory = sessionService.moveToNextStory(pokerSession.getId());

        if (nextStory != null) {
            broadcastToRoom(roomCode, PokerMessage.currentStoryChanged(nextStory.getId()));
        } else {
            // No more stories - complete session
            sessionService.completeSession(pokerSession.getId());
            broadcastToRoom(roomCode, PokerMessage.sessionCompleted());
        }
    }

    private void handleStartSession(WebSocketSession session, String roomCode, Map<String, Object> payload) throws IOException {
        // Check if facilitator
        ParticipantInfo participant = sessionParticipants.get(session.getId());
        if (participant == null || !participant.isFacilitator()) {
            sendError(session, "Only facilitator can start session");
            return;
        }

        PokerSessionEntity pokerSession = sessionService.getSessionByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        sessionService.startSession(pokerSession.getId());

        // Send updated state to all
        SessionState state = sessionService.buildSessionState(roomCode);
        broadcastToRoom(roomCode, PokerMessage.state(state));
    }

    private void handleCompleteSession(WebSocketSession session, String roomCode, Map<String, Object> payload) throws IOException {
        // Check if facilitator
        ParticipantInfo participant = sessionParticipants.get(session.getId());
        if (participant == null || !participant.isFacilitator()) {
            sendError(session, "Only facilitator can complete session");
            return;
        }

        PokerSessionEntity pokerSession = sessionService.getSessionByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));

        sessionService.completeSession(pokerSession.getId());
        broadcastToRoom(roomCode, PokerMessage.sessionCompleted());
    }

    private void broadcastToRoom(String roomCode, PokerMessage message) throws IOException {
        broadcastToRoom(roomCode, message, null);
    }

    private void broadcastToRoom(String roomCode, PokerMessage message, WebSocketSession exclude) throws IOException {
        CopyOnWriteArrayList<WebSocketSession> sessions = roomSessions.get(roomCode);
        if (sessions == null) return;

        String jsonMessage = objectMapper.writeValueAsString(message);
        TextMessage textMessage = new TextMessage(jsonMessage);

        for (WebSocketSession session : sessions) {
            if (session.isOpen() && (exclude == null || !session.equals(exclude))) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.error("Failed to send message to session: {}", e.getMessage());
                }
            }
        }
    }

    private void sendMessage(WebSocketSession session, PokerMessage message) throws IOException {
        String jsonMessage = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(jsonMessage));
    }

    private void sendError(WebSocketSession session, String errorMessage) throws IOException {
        sendMessage(session, PokerMessage.error(errorMessage));
    }

    private String extractRoomCode(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) return null;

        Map<String, String> variables = URI_TEMPLATE.match(uri.getPath());
        return variables != null ? variables.get("roomCode") : null;
    }
}
