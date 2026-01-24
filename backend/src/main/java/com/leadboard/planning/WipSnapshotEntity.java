package com.leadboard.planning;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Снапшот WIP статуса команды на определённую дату.
 * Используется для построения графика утилизации WIP во времени.
 */
@Entity
@Table(name = "wip_snapshots", indexes = {
        @Index(name = "idx_wip_snapshots_team_date", columnList = "team_id, snapshot_date")
})
public class WipSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    // Team-level WIP
    @Column(name = "team_wip_limit", nullable = false)
    private Integer teamWipLimit;

    @Column(name = "team_wip_current", nullable = false)
    private Integer teamWipCurrent;

    // Role-level WIP
    @Column(name = "sa_wip_limit")
    private Integer saWipLimit;

    @Column(name = "sa_wip_current")
    private Integer saWipCurrent;

    @Column(name = "dev_wip_limit")
    private Integer devWipLimit;

    @Column(name = "dev_wip_current")
    private Integer devWipCurrent;

    @Column(name = "qa_wip_limit")
    private Integer qaWipLimit;

    @Column(name = "qa_wip_current")
    private Integer qaWipCurrent;

    // Queue info
    @Column(name = "epics_in_queue")
    private Integer epicsInQueue;

    @Column(name = "total_epics")
    private Integer totalEpics;

    public WipSnapshotEntity() {
    }

    public WipSnapshotEntity(Long teamId, LocalDate snapshotDate) {
        this.teamId = teamId;
        this.snapshotDate = snapshotDate;
        this.createdAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getTeamWipLimit() {
        return teamWipLimit;
    }

    public void setTeamWipLimit(Integer teamWipLimit) {
        this.teamWipLimit = teamWipLimit;
    }

    public Integer getTeamWipCurrent() {
        return teamWipCurrent;
    }

    public void setTeamWipCurrent(Integer teamWipCurrent) {
        this.teamWipCurrent = teamWipCurrent;
    }

    public Integer getSaWipLimit() {
        return saWipLimit;
    }

    public void setSaWipLimit(Integer saWipLimit) {
        this.saWipLimit = saWipLimit;
    }

    public Integer getSaWipCurrent() {
        return saWipCurrent;
    }

    public void setSaWipCurrent(Integer saWipCurrent) {
        this.saWipCurrent = saWipCurrent;
    }

    public Integer getDevWipLimit() {
        return devWipLimit;
    }

    public void setDevWipLimit(Integer devWipLimit) {
        this.devWipLimit = devWipLimit;
    }

    public Integer getDevWipCurrent() {
        return devWipCurrent;
    }

    public void setDevWipCurrent(Integer devWipCurrent) {
        this.devWipCurrent = devWipCurrent;
    }

    public Integer getQaWipLimit() {
        return qaWipLimit;
    }

    public void setQaWipLimit(Integer qaWipLimit) {
        this.qaWipLimit = qaWipLimit;
    }

    public Integer getQaWipCurrent() {
        return qaWipCurrent;
    }

    public void setQaWipCurrent(Integer qaWipCurrent) {
        this.qaWipCurrent = qaWipCurrent;
    }

    public Integer getEpicsInQueue() {
        return epicsInQueue;
    }

    public void setEpicsInQueue(Integer epicsInQueue) {
        this.epicsInQueue = epicsInQueue;
    }

    public Integer getTotalEpics() {
        return totalEpics;
    }

    public void setTotalEpics(Integer totalEpics) {
        this.totalEpics = totalEpics;
    }
}
