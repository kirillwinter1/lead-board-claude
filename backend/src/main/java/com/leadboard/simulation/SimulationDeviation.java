package com.leadboard.simulation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
public class SimulationDeviation {

    private final SimulationProperties properties;
    private final Random random;

    @Autowired
    public SimulationDeviation(SimulationProperties properties) {
        this.properties = properties;
        this.random = new Random();
    }

    // For testing: allow injecting a seeded Random
    SimulationDeviation(SimulationProperties properties, Random random) {
        this.properties = properties;
        this.random = random;
    }

    /**
     * Applies daily deviation to base hours (±variance).
     * Example: baseHours=6, variance=0.3 → result in [4.2, 7.8]
     * Rounded to nearest 0.5h.
     */
    public double applyDailyDeviation(double baseHours) {
        double variance = properties.getDeviation().getDailyHoursVariance();
        double factor = 1.0 + (random.nextDouble() * 2 - 1) * variance;
        double result = baseHours * factor;
        return roundToHalf(Math.max(0.5, result));
    }

    /**
     * Rolls a speed deviation profile.
     * Returns a multiplier for the effective estimate:
     * - on-track (70%): 1.0
     * - early (15%): 0.8
     * - delay (10%): 1.3
     * - severe delay (5%): 1.7
     */
    public double rollSpeedDeviation() {
        double roll = random.nextDouble();
        SimulationProperties.DeviationConfig cfg = properties.getDeviation();

        double cumulative = cfg.getOnTrackChance();
        if (roll < cumulative) return 1.0;

        cumulative += cfg.getEarlyChance();
        if (roll < cumulative) return 0.8;

        cumulative += cfg.getDelayChance();
        if (roll < cumulative) return 1.3;

        return 1.7;
    }

    /**
     * Applies speed deviation to effective hours.
     */
    public double applySpeedDeviation(double effectiveHours, double speedFactor) {
        return roundToHalf(effectiveHours * speedFactor);
    }

    private double roundToHalf(double value) {
        return Math.round(value * 2.0) / 2.0;
    }
}
