package com.leadboard.poker.repository;

import com.leadboard.poker.entity.PokerStoryEntity;
import com.leadboard.poker.entity.PokerStoryEntity.StoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PokerStoryRepository extends JpaRepository<PokerStoryEntity, Long> {

    List<PokerStoryEntity> findBySessionIdOrderByOrderIndexAsc(Long sessionId);

    @Query("SELECT s FROM PokerStoryEntity s LEFT JOIN FETCH s.votes WHERE s.id = :id")
    Optional<PokerStoryEntity> findByIdWithVotes(@Param("id") Long id);

    @Query("SELECT s FROM PokerStoryEntity s LEFT JOIN FETCH s.votes WHERE s.session.id = :sessionId ORDER BY s.orderIndex ASC")
    List<PokerStoryEntity> findBySessionIdWithVotes(@Param("sessionId") Long sessionId);

    Optional<PokerStoryEntity> findBySessionIdAndStatus(Long sessionId, StoryStatus status);

    @Query("SELECT MAX(s.orderIndex) FROM PokerStoryEntity s WHERE s.session.id = :sessionId")
    Optional<Integer> findMaxOrderIndexBySessionId(@Param("sessionId") Long sessionId);

    @Query("SELECT s FROM PokerStoryEntity s WHERE s.session.id = :sessionId AND s.status = 'PENDING' ORDER BY s.orderIndex ASC")
    List<PokerStoryEntity> findPendingStoriesBySessionId(@Param("sessionId") Long sessionId);

    long countBySessionIdAndStatus(Long sessionId, StoryStatus status);
}
