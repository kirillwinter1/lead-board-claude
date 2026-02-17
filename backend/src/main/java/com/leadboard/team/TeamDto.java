package com.leadboard.team;

import java.time.OffsetDateTime;

public record TeamDto(
    Long id,
    String name,
    String jiraTeamValue,
    String color,
    Boolean active,
    int memberCount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static TeamDto from(TeamEntity entity) {
        int activeMembers = (int) entity.getMembers().stream()
                .filter(TeamMemberEntity::getActive)
                .count();
        return new TeamDto(
            entity.getId(),
            entity.getName(),
            entity.getJiraTeamValue(),
            entity.getColor(),
            entity.getActive(),
            activeMembers,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
