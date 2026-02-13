package com.leadboard.poker.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "poker_stories")
public class PokerStoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private PokerSessionEntity session;

    @Column(name = "story_key")
    private String storyKey;

    @Column(nullable = false)
    private String title;

    @Column(name = "needs_roles", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> needsRoles = new ArrayList<>();

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StoryStatus status = StoryStatus.PENDING;

    @Column(name = "final_estimates", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Integer> finalEstimates = new HashMap<>();

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PokerVoteEntity> votes = new ArrayList<>();

    public enum StoryStatus {
        PENDING, VOTING, REVEALED, COMPLETED
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PokerSessionEntity getSession() {
        return session;
    }

    public void setSession(PokerSessionEntity session) {
        this.session = session;
    }

    public String getStoryKey() {
        return storyKey;
    }

    public void setStoryKey(String storyKey) {
        this.storyKey = storyKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getNeedsRoles() {
        return needsRoles;
    }

    public void setNeedsRoles(List<String> needsRoles) {
        this.needsRoles = needsRoles != null ? needsRoles : new ArrayList<>();
    }

    public boolean needsRole(String roleCode) {
        return needsRoles != null && needsRoles.contains(roleCode);
    }

    public StoryStatus getStatus() {
        return status;
    }

    public void setStatus(StoryStatus status) {
        this.status = status;
    }

    public Map<String, Integer> getFinalEstimates() {
        return finalEstimates;
    }

    public void setFinalEstimates(Map<String, Integer> finalEstimates) {
        this.finalEstimates = finalEstimates != null ? finalEstimates : new HashMap<>();
    }

    public Integer getFinalEstimate(String roleCode) {
        return finalEstimates != null ? finalEstimates.get(roleCode) : null;
    }

    public void setFinalEstimate(String roleCode, Integer hours) {
        if (finalEstimates == null) {
            finalEstimates = new HashMap<>();
        }
        if (hours != null) {
            finalEstimates.put(roleCode, hours);
        }
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<PokerVoteEntity> getVotes() {
        return votes;
    }

    public void setVotes(List<PokerVoteEntity> votes) {
        this.votes = votes;
    }

    public void addVote(PokerVoteEntity vote) {
        votes.add(vote);
        vote.setStory(this);
    }
}
