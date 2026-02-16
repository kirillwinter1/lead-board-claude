package com.leadboard.competency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberCompetencyRepository extends JpaRepository<MemberCompetencyEntity, Long> {

    List<MemberCompetencyEntity> findByTeamMemberId(Long teamMemberId);

    List<MemberCompetencyEntity> findByTeamMemberIdIn(List<Long> teamMemberIds);

    Optional<MemberCompetencyEntity> findByTeamMemberIdAndComponentName(Long teamMemberId, String componentName);

    void deleteByTeamMemberId(Long teamMemberId);
}
