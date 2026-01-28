package com.leadboard.poker.dto;

import java.util.List;

/**
 * Full session state sent via WebSocket
 */
public record SessionState(
    Long sessionId,
    Long teamId,
    String epicKey,
    String status,
    String roomCode,
    String facilitatorAccountId,
    List<StoryResponse> stories,
    Long currentStoryId,
    List<ParticipantInfo> participants
) {}
