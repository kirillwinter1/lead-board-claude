package com.leadboard.planning;

import com.leadboard.planning.dto.ForecastResponse;
import com.leadboard.planning.dto.ForecastResponse.WipStatus;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис для управления снапшотами WIP.
 * Создаёт ежедневные снапшоты и предоставляет историю для графиков.
 *
 * ВАЖНО: С версии F21 (Unified Planning) WIP лимиты НЕ влияют на алгоритм планирования.
 * Этот сервис сохраняет историческую статистику количества эпиков в работе
 * для целей анализа и отчётности, но данные не используются при планировании.
 *
 * Фактически теперь снапшоты показывают:
 * - teamWipCurrent = totalEpics (все эпики "активны")
 * - epicsInQueue = 0 (очередь не используется)
 */
@Service
public class WipSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(WipSnapshotService.class);

    private final WipSnapshotRepository snapshotRepository;
    private final TeamRepository teamRepository;
    private final ForecastService forecastService;

    public WipSnapshotService(
            WipSnapshotRepository snapshotRepository,
            TeamRepository teamRepository,
            ForecastService forecastService
    ) {
        this.snapshotRepository = snapshotRepository;
        this.teamRepository = teamRepository;
        this.forecastService = forecastService;
    }

    /**
     * Создаёт снапшот WIP для команды на текущую дату.
     */
    @Transactional
    public WipSnapshotEntity createSnapshot(Long teamId) {
        LocalDate today = LocalDate.now();

        // Проверяем, нет ли уже снапшота на сегодня
        if (snapshotRepository.existsByTeamIdAndSnapshotDate(teamId, today)) {
            log.debug("Snapshot already exists for team {} on {}", teamId, today);
            return snapshotRepository.findTopByTeamIdOrderBySnapshotDateDesc(teamId).orElse(null);
        }

        // Получаем текущий прогноз для расчёта WIP
        ForecastResponse forecast = forecastService.calculateForecast(teamId);
        WipStatus wipStatus = forecast.wipStatus();

        WipSnapshotEntity snapshot = new WipSnapshotEntity(teamId, today);
        snapshot.setTeamWipLimit(wipStatus.limit());
        snapshot.setTeamWipCurrent(wipStatus.current());

        if (wipStatus.roleWip() != null) {
            Map<String, WipSnapshotEntity.RoleWipEntry> roleData = new LinkedHashMap<>();
            wipStatus.roleWip().forEach((role, wip) ->
                roleData.put(role, new WipSnapshotEntity.RoleWipEntry(wip.limit(), wip.current()))
            );
            snapshot.setRoleWipData(roleData);
        }

        // Считаем эпики в очереди
        int inQueue = (int) forecast.epics().stream()
                .filter(e -> !e.isWithinWip())
                .count();
        snapshot.setEpicsInQueue(inQueue);
        snapshot.setTotalEpics(forecast.epics().size());

        WipSnapshotEntity saved = snapshotRepository.save(snapshot);
        log.info("Created WIP snapshot for team {} on {}: WIP {}/{}",
                teamId, today, wipStatus.current(), wipStatus.limit());

        return saved;
    }

    /**
     * Получает историю WIP для команды за период.
     */
    public List<WipSnapshotEntity> getHistory(Long teamId, LocalDate from, LocalDate to) {
        return snapshotRepository.findByTeamIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                teamId, from, to);
    }

    /**
     * Получает историю WIP за последние N дней.
     */
    public List<WipSnapshotEntity> getRecentHistory(Long teamId, int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days);
        return getHistory(teamId, from, to);
    }

    /**
     * Scheduled job: создаёт ежедневные снапшоты для всех активных команд.
     * Запускается каждый день в 9:00.
     */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void createDailySnapshots() {
        log.info("Starting daily WIP snapshot creation");

        List<TeamEntity> activeTeams = teamRepository.findByActiveTrue();
        int created = 0;

        for (TeamEntity team : activeTeams) {
            try {
                createSnapshot(team.getId());
                created++;
            } catch (Exception e) {
                log.error("Failed to create snapshot for team {}: {}", team.getId(), e.getMessage());
            }
        }

        log.info("Completed daily WIP snapshots: {} created for {} teams", created, activeTeams.size());
    }

    /**
     * Scheduled job: удаляет старые снапшоты (старше 90 дней).
     * Запускается каждое воскресенье в 3:00.
     */
    @Scheduled(cron = "0 0 3 * * SUN")
    @Transactional
    public void cleanupOldSnapshots() {
        LocalDate cutoff = LocalDate.now().minusDays(90);
        snapshotRepository.deleteBySnapshotDateBefore(cutoff);
        log.info("Cleaned up WIP snapshots older than {}", cutoff);
    }
}
