package com.leadboard.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuthTokenRepository extends JpaRepository<OAuthTokenEntity, Long> {

    Optional<OAuthTokenEntity> findByUserId(Long userId);

    @Query("SELECT t FROM OAuthTokenEntity t JOIN FETCH t.user ORDER BY t.updatedAt DESC LIMIT 1")
    Optional<OAuthTokenEntity> findLatestToken();

    @Query("SELECT t FROM OAuthTokenEntity t JOIN FETCH t.user WHERE t.user.atlassianAccountId = :accountId")
    Optional<OAuthTokenEntity> findByAtlassianAccountId(String accountId);
}
