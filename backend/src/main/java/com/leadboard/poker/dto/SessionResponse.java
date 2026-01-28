package com.leadboard.poker.dto;

import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.entity.PokerStoryEntity;

import java.time.OffsetDateTime;
import java.util.List;

public record SessionResponse(
    Long id,
    Long teamId,
    String epicKey,
    String facilitatorAccountId,
    String status,
    String roomCode,
    OffsetDateTime createdAt,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    List<StoryResponse> stories,
    Long currentStoryId
) {
    public static SessionResponse from(PokerSessionEntity entity) {
        Long currentStoryId = entity.getStories().stream()
                .filter(s -> s.getStatus() == PokerStoryEntity.StoryStatus.VOTING)
                .findFirst()
                .map(PokerStoryEntity::getId)
                .orElse(null);

        return new SessionResponse(
                entity.getId(),
                entity.getTeamId(),
                entity.getEpicKey(),
                entity.getFacilitatorAccountId(),
                entity.getStatus().name(),
                entity.getRoomCode(),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getStories().stream().map(StoryResponse::from).toList(),
                currentStoryId
        );
    }
}
