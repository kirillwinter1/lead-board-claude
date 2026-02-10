package com.leadboard.simulation.dto;

public record SimulationAction(
        ActionType type,
        String issueKey,
        String issueType,
        String assignee,
        String fromStatus,
        String toStatus,
        Double hoursLogged,
        String reason,
        boolean executed,
        String error
) {
    public enum ActionType {
        TRANSITION,
        WORKLOG,
        SKIP
    }

    public static SimulationAction transition(String issueKey, String issueType, String assignee,
                                              String fromStatus, String toStatus, String reason) {
        return new SimulationAction(ActionType.TRANSITION, issueKey, issueType, assignee,
                fromStatus, toStatus, null, reason, false, null);
    }

    public static SimulationAction worklog(String issueKey, String issueType, String assignee,
                                           double hours, String reason) {
        return new SimulationAction(ActionType.WORKLOG, issueKey, issueType, assignee,
                null, null, hours, reason, false, null);
    }

    public static SimulationAction skip(String issueKey, String issueType, String assignee, String reason) {
        return new SimulationAction(ActionType.SKIP, issueKey, issueType, assignee,
                null, null, null, reason, false, null);
    }

    public SimulationAction withExecuted() {
        return new SimulationAction(type, issueKey, issueType, assignee,
                fromStatus, toStatus, hoursLogged, reason, true, null);
    }

    public SimulationAction withError(String errorMsg) {
        return new SimulationAction(type, issueKey, issueType, assignee,
                fromStatus, toStatus, hoursLogged, reason, false, errorMsg);
    }
}
