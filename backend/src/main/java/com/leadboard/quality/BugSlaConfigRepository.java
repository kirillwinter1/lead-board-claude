package com.leadboard.quality;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BugSlaConfigRepository extends JpaRepository<BugSlaConfigEntity, Long> {
    Optional<BugSlaConfigEntity> findByPriority(String priority);
}
