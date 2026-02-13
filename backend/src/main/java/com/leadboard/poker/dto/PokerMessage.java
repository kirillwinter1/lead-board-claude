package com.leadboard.poker.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * WebSocket message format for Planning Poker
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PokerMessage(
    String type,
    Map<String, Object> payload
) {
    // Message types from client
    public static final String TYPE_JOIN = "JOIN";
    public static final String TYPE_VOTE = "VOTE";
    public static final String TYPE_REVEAL = "REVEAL";
    public static final String TYPE_SET_FINAL = "SET_FINAL";
    public static final String TYPE_NEXT_STORY = "NEXT_STORY";
    public static final String TYPE_START_SESSION = "START_SESSION";
    public static final String TYPE_COMPLETE_SESSION = "COMPLETE_SESSION";

    // Message types from server
    public static final String TYPE_STATE = "STATE";
    public static final String TYPE_PARTICIPANT_JOINED = "PARTICIPANT_JOINED";
    public static final String TYPE_PARTICIPANT_LEFT = "PARTICIPANT_LEFT";
    public static final String TYPE_VOTE_CAST = "VOTE_CAST";
    public static final String TYPE_VOTES_REVEALED = "VOTES_REVEALED";
    public static final String TYPE_STORY_COMPLETED = "STORY_COMPLETED";
    public static final String TYPE_CURRENT_STORY_CHANGED = "CURRENT_STORY_CHANGED";
    public static final String TYPE_SESSION_STARTED = "SESSION_STARTED";
    public static final String TYPE_SESSION_COMPLETED = "SESSION_COMPLETED";
    public static final String TYPE_STORY_ADDED = "STORY_ADDED";
    public static final String TYPE_ERROR = "ERROR";

    public static PokerMessage state(SessionState state) {
        return new PokerMessage(TYPE_STATE, Map.of("session", state));
    }

    public static PokerMessage participantJoined(ParticipantInfo participant) {
        return new PokerMessage(TYPE_PARTICIPANT_JOINED, Map.of("participant", participant));
    }

    public static PokerMessage participantLeft(String accountId) {
        return new PokerMessage(TYPE_PARTICIPANT_LEFT, Map.of("accountId", accountId));
    }

    public static PokerMessage voteCast(Long storyId, String voterAccountId, String role) {
        return new PokerMessage(TYPE_VOTE_CAST, Map.of(
                "storyId", storyId,
                "voterAccountId", voterAccountId,
                "role", role
        ));
    }

    public static PokerMessage votesRevealed(Long storyId, java.util.List<VoteResponse> votes) {
        return new PokerMessage(TYPE_VOTES_REVEALED, Map.of(
                "storyId", storyId,
                "votes", votes
        ));
    }

    public static PokerMessage storyCompleted(Long storyId, Map<String, Integer> finalEstimates) {
        return new PokerMessage(TYPE_STORY_COMPLETED, Map.of(
                "storyId", storyId,
                "finalEstimates", finalEstimates != null ? finalEstimates : Map.of()
        ));
    }

    public static PokerMessage currentStoryChanged(Long storyId) {
        return new PokerMessage(TYPE_CURRENT_STORY_CHANGED, Map.of("storyId", storyId));
    }

    public static PokerMessage sessionStarted() {
        return new PokerMessage(TYPE_SESSION_STARTED, Map.of());
    }

    public static PokerMessage sessionCompleted() {
        return new PokerMessage(TYPE_SESSION_COMPLETED, Map.of());
    }

    public static PokerMessage storyAdded(StoryResponse story) {
        return new PokerMessage(TYPE_STORY_ADDED, Map.of("story", story));
    }

    public static PokerMessage error(String message) {
        return new PokerMessage(TYPE_ERROR, Map.of("message", message));
    }
}
