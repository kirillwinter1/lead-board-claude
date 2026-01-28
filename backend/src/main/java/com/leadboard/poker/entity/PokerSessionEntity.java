package com.leadboard.poker.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "poker_sessions")
public class PokerSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "epic_key", nullable = false)
    private String epicKey;

    @Column(name = "facilitator_account_id", nullable = false)
    private String facilitatorAccountId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.PREPARING;

    @Column(name = "room_code", nullable = false, unique = true)
    private String roomCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<PokerStoryEntity> stories = new ArrayList<>();

    public enum SessionStatus {
        PREPARING, ACTIVE, COMPLETED
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

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public String getEpicKey() {
        return epicKey;
    }

    public void setEpicKey(String epicKey) {
        this.epicKey = epicKey;
    }

    public String getFacilitatorAccountId() {
        return facilitatorAccountId;
    }

    public void setFacilitatorAccountId(String facilitatorAccountId) {
        this.facilitatorAccountId = facilitatorAccountId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public void setStatus(SessionStatus status) {
        this.status = status;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public List<PokerStoryEntity> getStories() {
        return stories;
    }

    public void setStories(List<PokerStoryEntity> stories) {
        this.stories = stories;
    }

    public void addStory(PokerStoryEntity story) {
        stories.add(story);
        story.setSession(this);
    }

    public void removeStory(PokerStoryEntity story) {
        stories.remove(story);
        story.setSession(null);
    }
}
