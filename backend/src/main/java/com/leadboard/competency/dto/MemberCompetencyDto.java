package com.leadboard.competency.dto;

import java.util.List;

public record MemberCompetencyDto(
        Long memberId,
        String displayName,
        List<CompetencyLevelDto> competencies
) {}
