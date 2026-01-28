package com.leadboard.forecast.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "forecast_snapshots")
public class ForecastSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unified_planning_json", columnDefinition = "jsonb", nullable = false)
    private String unifiedPlanningJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "forecast_json", columnDefinition = "jsonb", nullable = false)
    private String forecastJson;

    public ForecastSnapshotEntity() {
    }

    public ForecastSnapshotEntity(Long teamId, LocalDate snapshotDate, String unifiedPlanningJson, String forecastJson) {
        this.teamId = teamId;
        this.snapshotDate = snapshotDate;
        this.unifiedPlanningJson = unifiedPlanningJson;
        this.forecastJson = forecastJson;
        this.createdAt = OffsetDateTime.now();
    }

    // Getters and setters

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

    public String getUnifiedPlanningJson() {
        return unifiedPlanningJson;
    }

    public void setUnifiedPlanningJson(String unifiedPlanningJson) {
        this.unifiedPlanningJson = unifiedPlanningJson;
    }

    public String getForecastJson() {
        return forecastJson;
    }

    public void setForecastJson(String forecastJson) {
        this.forecastJson = forecastJson;
    }
}
