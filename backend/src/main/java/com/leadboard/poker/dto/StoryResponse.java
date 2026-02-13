package com.leadboard.poker.dto;

import com.leadboard.poker.entity.PokerStoryEntity;

import java.util.List;
import java.util.Map;

public record StoryResponse(
    Long id,
    String storyKey,
    String title,
    List<String> needsRoles,
    String status,
    Map<String, Integer> finalEstimates,
    Integer orderIndex,
    List<VoteResponse> votes
) {
    public static StoryResponse from(PokerStoryEntity entity) {
        return new StoryResponse(
                entity.getId(),
                entity.getStoryKey(),
                entity.getTitle(),
                entity.getNeedsRoles(),
                entity.getStatus().name(),
                entity.getFinalEstimates(),
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
                entity.getNeedsRoles(),
                entity.getStatus().name(),
                entity.getFinalEstimates(),
                entity.getOrderIndex(),
                entity.getVotes() != null
                        ? entity.getVotes().stream().map(VoteResponse::fromWithoutValue).toList()
                        : List.of()
        );
    }
}
