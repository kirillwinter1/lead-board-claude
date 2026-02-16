package com.leadboard.competency;

import com.leadboard.team.TeamMemberEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "member_competencies")
public class MemberCompetencyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_member_id", nullable = false)
    private TeamMemberEntity teamMember;

    @Column(name = "component_name", nullable = false, length = 200)
    private String componentName;

    @Column(name = "level", nullable = false)
    private int level = 3;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TeamMemberEntity getTeamMember() { return teamMember; }
    public void setTeamMember(TeamMemberEntity teamMember) { this.teamMember = teamMember; }

    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
