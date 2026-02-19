package com.leadboard.team.dto;

import com.leadboard.team.AbsenceType;
import com.leadboard.team.MemberAbsenceEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AbsenceDto(
        Long id,
        Long memberId,
        AbsenceType absenceType,
        LocalDate startDate,
        LocalDate endDate,
        String comment,
        OffsetDateTime createdAt
) {
    public static AbsenceDto from(MemberAbsenceEntity entity) {
        return new AbsenceDto(
                entity.getId(),
                entity.getMember().getId(),
                entity.getAbsenceType(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getComment(),
                entity.getCreatedAt()
        );
    }
}
