package com.leadboard.team;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TeamMemberDto(
    Long id,
    Long teamId,
    String jiraAccountId,
    String displayName,
    String role,
    Grade grade,
    BigDecimal hoursPerDay,
    Boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static TeamMemberDto from(TeamMemberEntity entity) {
        return new TeamMemberDto(
            entity.getId(),
            entity.getTeam().getId(),
            entity.getJiraAccountId(),
            entity.getDisplayName(),
            entity.getRole(),
            entity.getGrade(),
            entity.getHoursPerDay(),
            entity.getActive(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
