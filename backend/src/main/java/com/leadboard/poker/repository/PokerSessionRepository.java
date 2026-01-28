package com.leadboard.poker.repository;

import com.leadboard.poker.entity.PokerSessionEntity;
import com.leadboard.poker.entity.PokerSessionEntity.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PokerSessionRepository extends JpaRepository<PokerSessionEntity, Long> {

    Optional<PokerSessionEntity> findByRoomCode(String roomCode);

    @Query("SELECT s FROM PokerSessionEntity s LEFT JOIN FETCH s.stories WHERE s.roomCode = :roomCode")
    Optional<PokerSessionEntity> findByRoomCodeWithStories(@Param("roomCode") String roomCode);

    @Query("SELECT s FROM PokerSessionEntity s LEFT JOIN FETCH s.stories WHERE s.id = :id")
    Optional<PokerSessionEntity> findByIdWithStories(@Param("id") Long id);

    List<PokerSessionEntity> findByTeamIdOrderByCreatedAtDesc(Long teamId);

    List<PokerSessionEntity> findByTeamIdAndStatusOrderByCreatedAtDesc(Long teamId, SessionStatus status);

    List<PokerSessionEntity> findByEpicKeyOrderByCreatedAtDesc(String epicKey);

    boolean existsByRoomCode(String roomCode);

    @Query("SELECT s FROM PokerSessionEntity s WHERE s.status IN :statuses ORDER BY s.createdAt DESC")
    List<PokerSessionEntity> findByStatusIn(@Param("statuses") List<SessionStatus> statuses);
}
