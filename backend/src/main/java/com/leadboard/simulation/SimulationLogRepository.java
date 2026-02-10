package com.leadboard.simulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface SimulationLogRepository extends JpaRepository<SimulationLogEntity, Long> {

    List<SimulationLogEntity> findByTeamIdOrderBySimDateDesc(Long teamId);

    @Query("SELECT s FROM SimulationLogEntity s WHERE s.teamId = :teamId " +
           "AND s.simDate BETWEEN :from AND :to ORDER BY s.simDate DESC")
    List<SimulationLogEntity> findByTeamIdAndDateRange(
            @Param("teamId") Long teamId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    List<SimulationLogEntity> findByTeamIdAndSimDate(Long teamId, LocalDate simDate);

    boolean existsByStatus(String status);
}
