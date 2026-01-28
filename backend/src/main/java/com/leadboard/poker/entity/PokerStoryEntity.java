package com.leadboard.poker.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Column(name = "needs_sa", nullable = false)
    private boolean needsSa = false;

    @Column(name = "needs_dev", nullable = false)
    private boolean needsDev = false;

    @Column(name = "needs_qa", nullable = false)
    private boolean needsQa = false;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StoryStatus status = StoryStatus.PENDING;

    @Column(name = "final_sa_hours")
    private Integer finalSaHours;

    @Column(name = "final_dev_hours")
    private Integer finalDevHours;

    @Column(name = "final_qa_hours")
    private Integer finalQaHours;

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

    public boolean isNeedsSa() {
        return needsSa;
    }

    public void setNeedsSa(boolean needsSa) {
        this.needsSa = needsSa;
    }

    public boolean isNeedsDev() {
        return needsDev;
    }

    public void setNeedsDev(boolean needsDev) {
        this.needsDev = needsDev;
    }

    public boolean isNeedsQa() {
        return needsQa;
    }

    public void setNeedsQa(boolean needsQa) {
        this.needsQa = needsQa;
    }

    public StoryStatus getStatus() {
        return status;
    }

    public void setStatus(StoryStatus status) {
        this.status = status;
    }

    public Integer getFinalSaHours() {
        return finalSaHours;
    }

    public void setFinalSaHours(Integer finalSaHours) {
        this.finalSaHours = finalSaHours;
    }

    public Integer getFinalDevHours() {
        return finalDevHours;
    }

    public void setFinalDevHours(Integer finalDevHours) {
        this.finalDevHours = finalDevHours;
    }

    public Integer getFinalQaHours() {
        return finalQaHours;
    }

    public void setFinalQaHours(Integer finalQaHours) {
        this.finalQaHours = finalQaHours;
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
