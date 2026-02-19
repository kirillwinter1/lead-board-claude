package com.leadboard.team;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MemberAbsenceRepository extends JpaRepository<MemberAbsenceEntity, Long> {

    @Query("SELECT a FROM MemberAbsenceEntity a WHERE a.member.id = :memberId " +
           "AND a.startDate <= :rangeEnd AND a.endDate >= :rangeStart " +
           "ORDER BY a.startDate")
    List<MemberAbsenceEntity> findByMemberIdAndDateRange(
            @Param("memberId") Long memberId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd);

    @Query("SELECT a FROM MemberAbsenceEntity a WHERE a.member.team.id = :teamId " +
           "AND a.startDate <= :rangeEnd AND a.endDate >= :rangeStart " +
           "ORDER BY a.member.displayName, a.startDate")
    List<MemberAbsenceEntity> findByTeamIdAndDateRange(
            @Param("teamId") Long teamId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd);

    @Query("SELECT a FROM MemberAbsenceEntity a WHERE a.member.id = :memberId " +
           "AND a.endDate >= :today ORDER BY a.startDate")
    List<MemberAbsenceEntity> findUpcomingByMemberId(
            @Param("memberId") Long memberId,
            @Param("today") LocalDate today);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM MemberAbsenceEntity a " +
           "WHERE a.member.id = :memberId " +
           "AND a.startDate <= :endDate AND a.endDate >= :startDate " +
           "AND (:excludeId IS NULL OR a.id <> :excludeId)")
    boolean existsOverlapping(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeId") Long excludeId);
}
