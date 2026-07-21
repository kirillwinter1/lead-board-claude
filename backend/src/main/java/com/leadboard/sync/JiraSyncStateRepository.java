package com.leadboard.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface JiraSyncStateRepository extends JpaRepository<JiraSyncStateEntity, Long> {

    Optional<JiraSyncStateEntity> findByProjectKey(String projectKey);

    /**
     * Atomically claim the sync for a project: flip {@code sync_in_progress} false -> true in a
     * single conditional UPDATE and stamp the new start time. Returns the number of rows changed:
     * {@code 1} when this caller won the claim, {@code 0} when a sync was already in progress.
     *
     * <p>This closes the TOCTOU race between the scheduled sync and a manual trigger: both can
     * observe {@code sync_in_progress == false} in a non-atomic read-check-write and enter the
     * body, running a full sync twice and duplicating synthetic changelog entries. The DB-level
     * compare-and-set (WHERE sync_in_progress = false) lets exactly one of them succeed.
     */
    @Modifying
    @Transactional
    @Query("UPDATE JiraSyncStateEntity s SET s.syncInProgress = true, s.lastSyncStartedAt = :startedAt, "
            + "s.lastError = null WHERE s.projectKey = :projectKey AND s.syncInProgress = false")
    int tryStartSync(@Param("projectKey") String projectKey, @Param("startedAt") OffsetDateTime startedAt);
}
