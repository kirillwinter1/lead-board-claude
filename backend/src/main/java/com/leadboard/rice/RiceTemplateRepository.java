package com.leadboard.rice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiceTemplateRepository extends JpaRepository<RiceTemplateEntity, Long> {

    List<RiceTemplateEntity> findByActiveTrue();

    Optional<RiceTemplateEntity> findByCode(String code);
}
