package com.leadboard.config;

import com.leadboard.status.StatusMappingConfig;
import com.leadboard.status.StatusMappingService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Конфигурация функции rough estimate.
 * Статусы для проверки делегируются в StatusMappingService.
 */
@Component
@ConfigurationProperties(prefix = "rough-estimate")
public class RoughEstimateProperties {

    private boolean enabled = true;
    private BigDecimal stepDays = new BigDecimal("0.1");
    private BigDecimal minDays = BigDecimal.ZERO;
    private BigDecimal maxDays = new BigDecimal("365");

    private StatusMappingService statusMappingService;

    // Setter injection to avoid circular dependency issues
    @org.springframework.beans.factory.annotation.Autowired
    public void setStatusMappingService(StatusMappingService statusMappingService) {
        this.statusMappingService = statusMappingService;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getStepDays() {
        return stepDays;
    }

    public void setStepDays(BigDecimal stepDays) {
        this.stepDays = stepDays;
    }

    public BigDecimal getMinDays() {
        return minDays;
    }

    public void setMinDays(BigDecimal minDays) {
        this.minDays = minDays;
    }

    public BigDecimal getMaxDays() {
        return maxDays;
    }

    public void setMaxDays(BigDecimal maxDays) {
        this.maxDays = maxDays;
    }

    /**
     * Проверяет, допустим ли статус эпика для rough estimate.
     * Использует системную конфигурацию (без override команды).
     */
    public boolean isStatusAllowed(String status) {
        if (statusMappingService == null) {
            // Fallback если сервис ещё не инжектирован
            return true;
        }
        return statusMappingService.isAllowedForRoughEstimate(status, null);
    }

    /**
     * Проверяет, допустим ли статус эпика для rough estimate с учётом конфигурации команды.
     */
    public boolean isStatusAllowed(String status, StatusMappingConfig teamOverride) {
        if (statusMappingService == null) {
            return true;
        }
        return statusMappingService.isAllowedForRoughEstimate(status, teamOverride);
    }

    /**
     * Возвращает список допустимых статусов из системной конфигурации.
     * Для отображения в UI.
     */
    public List<String> getAllowedEpicStatuses() {
        if (statusMappingService == null) {
            return List.of();
        }
        return statusMappingService.getDefaultConfig().epicWorkflow().todoStatuses();
    }
}
