package com.leadboard.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<SessionEntity, String> {

    @Query("SELECT s FROM SessionEntity s JOIN FETCH s.user WHERE s.id = :id AND s.expiresAt > :now")
    Optional<SessionEntity> findValidSession(String id, OffsetDateTime now);

    @Modifying
    @Query("DELETE FROM SessionEntity s WHERE s.user.id = :userId")
    void deleteByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM SessionEntity s WHERE s.expiresAt <= :now")
    void deleteExpiredSessions(OffsetDateTime now);
}
