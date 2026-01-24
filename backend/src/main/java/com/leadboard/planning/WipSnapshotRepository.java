package com.leadboard.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WipSnapshotRepository extends JpaRepository<WipSnapshotEntity, Long> {

    /**
     * Получает снапшоты команды за период.
     */
    List<WipSnapshotEntity> findByTeamIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long teamId, LocalDate from, LocalDate to);

    /**
     * Получает последний снапшот для команды.
     */
    Optional<WipSnapshotEntity> findTopByTeamIdOrderBySnapshotDateDesc(Long teamId);

    /**
     * Проверяет существует ли снапшот на дату.
     */
    boolean existsByTeamIdAndSnapshotDate(Long teamId, LocalDate date);

    /**
     * Удаляет старые снапшоты (для очистки).
     */
    void deleteBySnapshotDateBefore(LocalDate date);
}
