package com.leadboard.forecast.repository;

import com.leadboard.forecast.entity.ForecastSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ForecastSnapshotRepository extends JpaRepository<ForecastSnapshotEntity, Long> {

    /**
     * Find snapshot for a specific team and date.
     */
    Optional<ForecastSnapshotEntity> findByTeamIdAndSnapshotDate(Long teamId, LocalDate snapshotDate);

    /**
     * Find all snapshots for a team within a date range.
     */
    @Query("SELECT s FROM ForecastSnapshotEntity s WHERE s.teamId = :teamId " +
           "AND s.snapshotDate BETWEEN :from AND :to ORDER BY s.snapshotDate")
    List<ForecastSnapshotEntity> findByTeamIdAndDateRange(
            @Param("teamId") Long teamId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /**
     * Find the latest snapshot for a team.
     */
    Optional<ForecastSnapshotEntity> findTopByTeamIdOrderBySnapshotDateDesc(Long teamId);

    /**
     * Find all available snapshot dates for a team.
     */
    @Query("SELECT s.snapshotDate FROM ForecastSnapshotEntity s WHERE s.teamId = :teamId ORDER BY s.snapshotDate DESC")
    List<LocalDate> findAvailableDatesByTeamId(@Param("teamId") Long teamId);

    /**
     * Check if snapshot exists for a team and date.
     */
    boolean existsByTeamIdAndSnapshotDate(Long teamId, LocalDate snapshotDate);

    /**
     * Delete old snapshots (for cleanup).
     */
    @Modifying
    @Query("DELETE FROM ForecastSnapshotEntity s WHERE s.teamId = :teamId AND s.snapshotDate < :before")
    void deleteOldSnapshots(@Param("teamId") Long teamId, @Param("before") LocalDate before);
}
