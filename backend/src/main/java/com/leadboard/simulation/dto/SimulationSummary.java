package com.leadboard.simulation.dto;

import java.util.List;

public record SimulationSummary(
        int totalActions,
        int transitionsExecuted,
        int worklogsExecuted,
        int assignmentsExecuted,
        int skipped,
        int errors,
        double totalHoursLogged
) {
    public static SimulationSummary fromActions(List<SimulationAction> actions) {
        int transitions = 0;
        int worklogs = 0;
        int assignments = 0;
        int skipped = 0;
        int errors = 0;
        double totalHours = 0;

        for (SimulationAction action : actions) {
            switch (action.type()) {
                case TRANSITION -> {
                    if (action.executed()) transitions++;
                    else if (action.error() != null) errors++;
                }
                case WORKLOG -> {
                    if (action.executed()) {
                        worklogs++;
                        if (action.hoursLogged() != null) totalHours += action.hoursLogged();
                    } else if (action.error() != null) errors++;
                }
                case ASSIGN -> {
                    if (action.executed()) assignments++;
                    else if (action.error() != null) errors++;
                }
                case SKIP -> skipped++;
            }
        }

        return new SimulationSummary(actions.size(), transitions, worklogs, assignments, skipped, errors, totalHours);
    }
}
