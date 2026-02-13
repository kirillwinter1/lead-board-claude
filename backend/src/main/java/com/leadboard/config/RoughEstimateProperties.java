package com.leadboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Конфигурация функции rough estimate (pure config, no service dependencies).
 * Status checking is done via WorkflowConfigService directly by callers.
 */
@Component
@ConfigurationProperties(prefix = "rough-estimate")
public class RoughEstimateProperties {

    private boolean enabled = true;
    private BigDecimal stepDays = new BigDecimal("0.1");
    private BigDecimal minDays = BigDecimal.ZERO;
    private BigDecimal maxDays = new BigDecimal("365");

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
}
