package com.leadboard.poker.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "poker_votes")
public class PokerVoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "story_id", nullable = false)
    private PokerStoryEntity story;

    @Column(name = "voter_account_id", nullable = false)
    private String voterAccountId;

    @Column(name = "voter_display_name")
    private String voterDisplayName;

    @Column(name = "voter_role", nullable = false)
    @Enumerated(EnumType.STRING)
    private VoterRole voterRole;

    @Column(name = "vote_hours")
    private Integer voteHours; // NULL = not voted, -1 = "?"

    @Column(name = "voted_at")
    private OffsetDateTime votedAt;

    public enum VoterRole {
        SA, DEV, QA
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PokerStoryEntity getStory() {
        return story;
    }

    public void setStory(PokerStoryEntity story) {
        this.story = story;
    }

    public String getVoterAccountId() {
        return voterAccountId;
    }

    public void setVoterAccountId(String voterAccountId) {
        this.voterAccountId = voterAccountId;
    }

    public String getVoterDisplayName() {
        return voterDisplayName;
    }

    public void setVoterDisplayName(String voterDisplayName) {
        this.voterDisplayName = voterDisplayName;
    }

    public VoterRole getVoterRole() {
        return voterRole;
    }

    public void setVoterRole(VoterRole voterRole) {
        this.voterRole = voterRole;
    }

    public Integer getVoteHours() {
        return voteHours;
    }

    public void setVoteHours(Integer voteHours) {
        this.voteHours = voteHours;
    }

    public OffsetDateTime getVotedAt() {
        return votedAt;
    }

    public void setVotedAt(OffsetDateTime votedAt) {
        this.votedAt = votedAt;
    }

    public boolean hasVoted() {
        return voteHours != null;
    }
}
