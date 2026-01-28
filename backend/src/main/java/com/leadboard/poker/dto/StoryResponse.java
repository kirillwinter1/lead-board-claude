package com.leadboard.poker.dto;

import com.leadboard.poker.entity.PokerStoryEntity;

import java.util.List;

public record StoryResponse(
    Long id,
    String storyKey,
    String title,
    boolean needsSa,
    boolean needsDev,
    boolean needsQa,
    String status,
    Integer finalSaHours,
    Integer finalDevHours,
    Integer finalQaHours,
    Integer orderIndex,
    List<VoteResponse> votes
) {
    public static StoryResponse from(PokerStoryEntity entity) {
        return new StoryResponse(
                entity.getId(),
                entity.getStoryKey(),
                entity.getTitle(),
                entity.isNeedsSa(),
                entity.isNeedsDev(),
                entity.isNeedsQa(),
                entity.getStatus().name(),
                entity.getFinalSaHours(),
                entity.getFinalDevHours(),
                entity.getFinalQaHours(),
                entity.getOrderIndex(),
                entity.getVotes() != null
                        ? entity.getVotes().stream().map(VoteResponse::from).toList()
                        : List.of()
        );
    }

    // Version without votes (for hiding during voting)
    public static StoryResponse fromWithoutVoteValues(PokerStoryEntity entity) {
        return new StoryResponse(
                entity.getId(),
                entity.getStoryKey(),
                entity.getTitle(),
                entity.isNeedsSa(),
                entity.isNeedsDev(),
                entity.isNeedsQa(),
                entity.getStatus().name(),
                entity.getFinalSaHours(),
                entity.getFinalDevHours(),
                entity.getFinalQaHours(),
                entity.getOrderIndex(),
                entity.getVotes() != null
                        ? entity.getVotes().stream().map(VoteResponse::fromWithoutValue).toList()
                        : List.of()
        );
    }
}
