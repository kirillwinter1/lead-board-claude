package com.leadboard.poker.dto;

import com.leadboard.poker.entity.PokerVoteEntity;

import java.time.OffsetDateTime;

public record VoteResponse(
    Long id,
    String voterAccountId,
    String voterDisplayName,
    String voterRole,
    Integer voteHours,
    boolean hasVoted,
    OffsetDateTime votedAt
) {
    public static VoteResponse from(PokerVoteEntity entity) {
        return new VoteResponse(
                entity.getId(),
                entity.getVoterAccountId(),
                entity.getVoterDisplayName(),
                entity.getVoterRole().name(),
                entity.getVoteHours(),
                entity.hasVoted(),
                entity.getVotedAt()
        );
    }

    // Version without showing the actual vote value (for hidden voting)
    public static VoteResponse fromWithoutValue(PokerVoteEntity entity) {
        return new VoteResponse(
                entity.getId(),
                entity.getVoterAccountId(),
                entity.getVoterDisplayName(),
                entity.getVoterRole().name(),
                null, // hide the actual value
                entity.hasVoted(),
                entity.getVotedAt()
        );
    }
}
