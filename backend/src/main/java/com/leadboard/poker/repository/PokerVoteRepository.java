package com.leadboard.poker.repository;

import com.leadboard.poker.entity.PokerVoteEntity;
import com.leadboard.poker.entity.PokerVoteEntity.VoterRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PokerVoteRepository extends JpaRepository<PokerVoteEntity, Long> {

    List<PokerVoteEntity> findByStoryId(Long storyId);

    Optional<PokerVoteEntity> findByStoryIdAndVoterAccountIdAndVoterRole(
            Long storyId, String voterAccountId, VoterRole voterRole);

    List<PokerVoteEntity> findByStoryIdAndVoterRole(Long storyId, VoterRole voterRole);

    @Query("SELECT v FROM PokerVoteEntity v WHERE v.story.id = :storyId AND v.voteHours IS NOT NULL")
    List<PokerVoteEntity> findCompletedVotesByStoryId(@Param("storyId") Long storyId);

    @Query("SELECT COUNT(v) FROM PokerVoteEntity v WHERE v.story.id = :storyId AND v.voteHours IS NOT NULL")
    long countCompletedVotesByStoryId(@Param("storyId") Long storyId);

    @Query("SELECT COUNT(v) FROM PokerVoteEntity v WHERE v.story.id = :storyId")
    long countTotalVotersByStoryId(@Param("storyId") Long storyId);

    void deleteByStoryId(Long storyId);
}
