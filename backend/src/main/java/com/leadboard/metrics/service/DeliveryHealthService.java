package com.leadboard.metrics.service;

import com.leadboard.metrics.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DeliveryHealthService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryHealthService.class);

    private final DsrService dsrService;
    private final TeamMetricsService metricsService;
    private final VelocityService velocityService;

    public DeliveryHealthService(DsrService dsrService,
                                  TeamMetricsService metricsService,
                                  VelocityService velocityService) {
        this.dsrService = dsrService;
        this.metricsService = metricsService;
        this.velocityService = velocityService;
    }

    public DeliveryHealth calculateHealth(Long teamId, LocalDate from, LocalDate to) {
        List<DeliveryHealth.HealthDimension> dimensions = new ArrayList<>();
        List<DeliveryHealth.RiskAlert> alerts = new ArrayList<>();

        BigDecimal predictabilityScore = BigDecimal.ZERO;
        BigDecimal speedScore = BigDecimal.ZERO;
        BigDecimal capacityScore = BigDecimal.ZERO;
        BigDecimal qualityScore = BigDecimal.valueOf(50); // default
        boolean hasPredictability = false;
        boolean hasSpeed = false;
        boolean hasCapacity = false;

        // 1. Predictability (weight 0.30)
        try {
            var dsr = dsrService.calculateDsr(teamId, from, to);
            if (dsr.totalEpics() > 0) {
                predictabilityScore = dsr.onTimeRate().setScale(0, RoundingMode.HALF_UP);
                hasPredictability = true;

                if (dsr.onTimeRate().compareTo(BigDecimal.valueOf(50)) < 0) {
                    alerts.add(new DeliveryHealth.RiskAlert("CRITICAL",
                            "Low Predictability",
                            "On-time delivery rate is below 50%",
                            "predictability",
                            "Review estimation practices and scope control"));
                } else if (dsr.onTimeRate().compareTo(BigDecimal.valueOf(70)) < 0) {
                    alerts.add(new DeliveryHealth.RiskAlert("WARNING",
                            "Predictability Declining",
                            "On-time delivery rate is below 70%",
                            "predictability",
                            "Consider breaking down large epics"));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to compute predictability for team {}", teamId, e);
        }

        // 2. Speed (weight 0.25) — cycle time vs 5d target
        try {
            var cycleTime = metricsService.calculateCycleTime(teamId, from, to, null, null, null);
            if (cycleTime.sampleSize() > 0) {
                BigDecimal target = BigDecimal.valueOf(5);
                BigDecimal median = cycleTime.medianDays();
                if (median.compareTo(target) <= 0) {
                    speedScore = BigDecimal.valueOf(100);
                } else {
                    // Linear decrease: at 2x target = 0
                    BigDecimal ratio = median.divide(target, 4, RoundingMode.HALF_UP);
                    speedScore = BigDecimal.valueOf(100)
                            .subtract(ratio.subtract(BigDecimal.ONE)
                                    .multiply(BigDecimal.valueOf(100)))
                            .max(BigDecimal.ZERO);
                }
                hasSpeed = true;

                if (median.compareTo(target.multiply(BigDecimal.valueOf(2))) > 0) {
                    alerts.add(new DeliveryHealth.RiskAlert("CRITICAL",
                            "Cycle Time Too High",
                            String.format("Median cycle time %.1fd is > 2x target of %.0fd", median, target),
                            "cycleTime",
                            "Investigate bottlenecks in workflow"));
                }
            }
        } catch (Exception e) {
            log.debug("Failed to compute speed for team {}", teamId, e);
        }

        // 3. Capacity (weight 0.25) — from velocity utilization
        try {
            var velocity = velocityService.calculateVelocity(teamId, from, to);
            BigDecimal util = velocity.utilizationPercent();
            // Ideal zone: 80-100%. Score decreases outside
            if (util.compareTo(BigDecimal.valueOf(80)) >= 0 && util.compareTo(BigDecimal.valueOf(100)) <= 0) {
                capacityScore = BigDecimal.valueOf(100);
            } else if (util.compareTo(BigDecimal.valueOf(80)) < 0) {
                capacityScore = util.divide(BigDecimal.valueOf(80), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).max(BigDecimal.ZERO);
            } else {
                // Over 100%: penalize linearly
                BigDecimal overRatio = util.subtract(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(50), 4, RoundingMode.HALF_UP);
                capacityScore = BigDecimal.valueOf(100)
                        .subtract(overRatio.multiply(BigDecimal.valueOf(100)))
                        .max(BigDecimal.ZERO);
            }
            hasCapacity = true;

            if (util.compareTo(BigDecimal.valueOf(130)) > 0) {
                alerts.add(new DeliveryHealth.RiskAlert("CRITICAL",
                        "Team Overloaded",
                        String.format("Utilization at %.0f%% exceeds 130%%", util),
                        "utilization",
                        "Consider adding capacity or reducing scope"));
            } else if (util.compareTo(BigDecimal.valueOf(110)) > 0) {
                alerts.add(new DeliveryHealth.RiskAlert("WARNING",
                        "High Utilization",
                        String.format("Utilization at %.0f%%", util),
                        "utilization",
                        "Monitor workload and consider redistributing"));
            } else if (util.compareTo(BigDecimal.valueOf(70)) < 0) {
                alerts.add(new DeliveryHealth.RiskAlert("WARNING",
                        "Low Utilization",
                        String.format("Utilization at %.0f%%", util),
                        "utilization",
                        "Capacity available for reallocation"));
            }
        } catch (Exception e) {
            log.debug("Failed to compute capacity for team {}", teamId, e);
        }

        // 4. Quality (weight 0.20) — based on throughput trend
        try {
            var throughput = metricsService.calculateThroughput(teamId, from, to, null, null, null);
            qualityScore = throughput.total() > 0 ? BigDecimal.valueOf(70) : BigDecimal.valueOf(30);
        } catch (Exception e) {
            log.debug("Failed to compute quality for team {}", teamId, e);
        }

        // Build dimensions with adaptive weights
        BigDecimal w1 = new BigDecimal("0.30");
        BigDecimal w2 = new BigDecimal("0.25");
        BigDecimal w3 = new BigDecimal("0.25");
        BigDecimal w4 = new BigDecimal("0.20");

        // Redistribute weights if dimensions are missing
        int availableCount = (hasPredictability ? 1 : 0) + (hasSpeed ? 1 : 0) + (hasCapacity ? 1 : 0) + 1; // quality always has a score
        if (availableCount < 4) {
            BigDecimal each = BigDecimal.ONE.divide(BigDecimal.valueOf(availableCount), 4, RoundingMode.HALF_UP);
            w1 = hasPredictability ? each : BigDecimal.ZERO;
            w2 = hasSpeed ? each : BigDecimal.ZERO;
            w3 = hasCapacity ? each : BigDecimal.ZERO;
            w4 = each;
        }

        dimensions.add(new DeliveryHealth.HealthDimension("Predictability", predictabilityScore, w1, getStatus(predictabilityScore)));
        dimensions.add(new DeliveryHealth.HealthDimension("Speed", speedScore, w2, getStatus(speedScore)));
        dimensions.add(new DeliveryHealth.HealthDimension("Capacity", capacityScore, w3, getStatus(capacityScore)));
        dimensions.add(new DeliveryHealth.HealthDimension("Quality", qualityScore, w4, getStatus(qualityScore)));

        // Composite score
        BigDecimal composite = predictabilityScore.multiply(w1)
                .add(speedScore.multiply(w2))
                .add(capacityScore.multiply(w3))
                .add(qualityScore.multiply(w4))
                .setScale(0, RoundingMode.HALF_UP);

        String grade = getGrade(composite);

        // Sort alerts by severity, take top 5
        alerts.sort(Comparator.comparingInt(a -> switch (a.severity()) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        }));
        if (alerts.size() > 5) {
            alerts = new ArrayList<>(alerts.subList(0, 5));
        }

        return new DeliveryHealth(composite, grade, dimensions, alerts);
    }

    private String getGrade(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) return "A";
        if (score.compareTo(BigDecimal.valueOf(75)) >= 0) return "B";
        if (score.compareTo(BigDecimal.valueOf(60)) >= 0) return "C";
        if (score.compareTo(BigDecimal.valueOf(45)) >= 0) return "D";
        return "F";
    }

    private String getStatus(BigDecimal score) {
        if (score.compareTo(BigDecimal.valueOf(75)) >= 0) return "GOOD";
        if (score.compareTo(BigDecimal.valueOf(45)) >= 0) return "WARNING";
        return "CRITICAL";
    }
}
