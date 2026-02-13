package com.leadboard.config.repository;

import com.leadboard.config.entity.TrackerMetadataCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrackerMetadataCacheRepository extends JpaRepository<TrackerMetadataCacheEntity, Long> {
    Optional<TrackerMetadataCacheEntity> findByCacheKey(String cacheKey);
    void deleteByCacheKey(String cacheKey);
}
