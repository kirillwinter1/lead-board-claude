package com.leadboard.competency.dto;

import java.util.List;

public record TeamCompetencyMatrixDto(
        List<String> components,
        List<MemberCompetencyDto> members
) {}
