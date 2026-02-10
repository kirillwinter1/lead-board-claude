package com.leadboard.simulation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "simulation")
public class SimulationProperties {

    private boolean enabled = false;
    private String cron = "0 0 19 * * MON-FRI";
    private List<Long> teamIds = List.of();
    private DeviationConfig deviation = new DeviationConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public List<Long> getTeamIds() {
        return teamIds;
    }

    public void setTeamIds(List<Long> teamIds) {
        this.teamIds = teamIds;
    }

    public DeviationConfig getDeviation() {
        return deviation;
    }

    public void setDeviation(DeviationConfig deviation) {
        this.deviation = deviation;
    }

    public static class DeviationConfig {
        private double dailyHoursVariance = 0.3;
        private double onTrackChance = 0.70;
        private double earlyChance = 0.15;
        private double delayChance = 0.10;
        private double severeDelayChance = 0.05;

        public double getDailyHoursVariance() {
            return dailyHoursVariance;
        }

        public void setDailyHoursVariance(double dailyHoursVariance) {
            this.dailyHoursVariance = dailyHoursVariance;
        }

        public double getOnTrackChance() {
            return onTrackChance;
        }

        public void setOnTrackChance(double onTrackChance) {
            this.onTrackChance = onTrackChance;
        }

        public double getEarlyChance() {
            return earlyChance;
        }

        public void setEarlyChance(double earlyChance) {
            this.earlyChance = earlyChance;
        }

        public double getDelayChance() {
            return delayChance;
        }

        public void setDelayChance(double delayChance) {
            this.delayChance = delayChance;
        }

        public double getSevereDelayChance() {
            return severeDelayChance;
        }

        public void setSevereDelayChance(double severeDelayChance) {
            this.severeDelayChance = severeDelayChance;
        }
    }
}
