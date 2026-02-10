package com.leadboard.simulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SimulationDeviationTest {

    private SimulationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new SimulationProperties();
    }

    @Test
    void applyDailyDeviation_shouldReturnValueWithinRange() {
        SimulationDeviation deviation = new SimulationDeviation(properties);
        double baseHours = 6.0;

        for (int i = 0; i < 100; i++) {
            double result = deviation.applyDailyDeviation(baseHours);
            // With 30% variance: 6 * 0.7 = 4.2, 6 * 1.3 = 7.8
            assertTrue(result >= 0.5, "Result should be at least 0.5, got: " + result);
            assertTrue(result <= 10.0, "Result should be reasonable, got: " + result);
            // Check rounding to 0.5
            assertEquals(0.0, result % 0.5, 0.001, "Result should be rounded to 0.5: " + result);
        }
    }

    @Test
    void applyDailyDeviation_withSeededRandom_shouldBeReproducible() {
        Random seeded = new Random(42);
        SimulationDeviation deviation = new SimulationDeviation(properties, seeded);

        double result1 = deviation.applyDailyDeviation(6.0);

        Random seeded2 = new Random(42);
        SimulationDeviation deviation2 = new SimulationDeviation(properties, seeded2);

        double result2 = deviation2.applyDailyDeviation(6.0);

        assertEquals(result1, result2, 0.001);
    }

    @Test
    void rollSpeedDeviation_shouldReturnValidMultipliers() {
        SimulationDeviation deviation = new SimulationDeviation(properties);

        int onTrack = 0, early = 0, delay = 0, severe = 0;

        for (int i = 0; i < 10000; i++) {
            double factor = deviation.rollSpeedDeviation();
            if (factor == 1.0) onTrack++;
            else if (factor == 0.8) early++;
            else if (factor == 1.3) delay++;
            else if (factor == 1.7) severe++;
            else fail("Unexpected speed factor: " + factor);
        }

        // Check distribution roughly matches config (70/15/10/5)
        assertTrue(onTrack > 6000, "On-track should be ~70%, got: " + onTrack);
        assertTrue(early > 1000, "Early should be ~15%, got: " + early);
        assertTrue(delay > 500, "Delay should be ~10%, got: " + delay);
        assertTrue(severe > 200, "Severe delay should be ~5%, got: " + severe);
    }

    @Test
    void applySpeedDeviation_shouldMultiplyCorrectly() {
        SimulationDeviation deviation = new SimulationDeviation(properties);

        assertEquals(10.0, deviation.applySpeedDeviation(10.0, 1.0));
        assertEquals(8.0, deviation.applySpeedDeviation(10.0, 0.8));
        assertEquals(13.0, deviation.applySpeedDeviation(10.0, 1.3));
        assertEquals(17.0, deviation.applySpeedDeviation(10.0, 1.7));
    }

    @Test
    void applySpeedDeviation_shouldRoundToHalf() {
        SimulationDeviation deviation = new SimulationDeviation(properties);

        double result = deviation.applySpeedDeviation(7.0, 1.3); // 9.1 → 9.0
        assertEquals(0.0, result % 0.5, 0.001, "Result should be rounded to 0.5: " + result);
    }

    @Test
    void applyDailyDeviation_smallBaseHours_shouldNotGoBelowMinimum() {
        SimulationDeviation deviation = new SimulationDeviation(properties);

        for (int i = 0; i < 100; i++) {
            double result = deviation.applyDailyDeviation(1.0);
            assertTrue(result >= 0.5, "Should not go below 0.5h, got: " + result);
        }
    }
}
