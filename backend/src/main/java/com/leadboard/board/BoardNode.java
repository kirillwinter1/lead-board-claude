package com.leadboard.board;

import com.leadboard.quality.DataQualityViolation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BoardNode {

    private String issueKey;
    private String title;
    private String status;
    private String issueType;
    private String jiraUrl;
    private String role; // workflow role code (for sub-tasks)
    private Long teamId;
    private String teamName;
    private Long estimateSeconds;
    private Long loggedSeconds;
    private Integer progress; // 0-100
    private Map<String, RoleMetrics> roleProgress; // dynamic role progress by role code
    private boolean epicInTodo; // true if Epic is in Backlog/To Do status (for UI styling)
    private Map<String, BigDecimal> roughEstimates; // dynamic rough estimates by role code
    private BigDecimal autoScore; // AutoScore for prioritization (Epic and Story)
    private Integer manualOrder; // Manual order position (1 = first)
    private Boolean flagged; // Impediment flag (Story only)
    private List<String> blocks; // Stories blocked by this story (Story only)
    private List<String> blockedBy; // Stories blocking this story (Story only)
    private LocalDate expectedDone; // Expected completion date (Story only, calculated)
    private String assigneeAccountId; // Assignee Jira account ID (Story only)
    private String assigneeDisplayName; // Assignee display name (Story only)
    private String parentProjectKey; // Parent PROJECT issue key (Epic only)
    private List<DataQualityViolation> alerts = new ArrayList<>(); // data quality violations
    private List<BoardNode> children = new ArrayList<>();

    public BoardNode() {
    }

    public BoardNode(String issueKey, String title, String status, String issueType, String jiraUrl) {
        this.issueKey = issueKey;
        this.title = title;
        this.status = status;
        this.issueType = issueType;
        this.jiraUrl = jiraUrl;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getJiraUrl() {
        return jiraUrl;
    }

    public void setJiraUrl(String jiraUrl) {
        this.jiraUrl = jiraUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Long getTeamId() {
        return teamId;
    }

    public void setTeamId(Long teamId) {
        this.teamId = teamId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public Long getEstimateSeconds() {
        return estimateSeconds;
    }

    public void setEstimateSeconds(Long estimateSeconds) {
        this.estimateSeconds = estimateSeconds;
    }

    public Long getLoggedSeconds() {
        return loggedSeconds;
    }

    public void setLoggedSeconds(Long loggedSeconds) {
        this.loggedSeconds = loggedSeconds;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Map<String, RoleMetrics> getRoleProgress() {
        return roleProgress;
    }

    public void setRoleProgress(Map<String, RoleMetrics> roleProgress) {
        this.roleProgress = roleProgress;
    }

    public boolean isEpicInTodo() {
        return epicInTodo;
    }

    public void setEpicInTodo(boolean epicInTodo) {
        this.epicInTodo = epicInTodo;
    }

    public Map<String, BigDecimal> getRoughEstimates() {
        return roughEstimates;
    }

    public void setRoughEstimates(Map<String, BigDecimal> roughEstimates) {
        this.roughEstimates = roughEstimates;
    }

    public BigDecimal getAutoScore() {
        return autoScore;
    }

    public void setAutoScore(BigDecimal autoScore) {
        this.autoScore = autoScore;
    }

    public Integer getManualOrder() {
        return manualOrder;
    }

    public void setManualOrder(Integer manualOrder) {
        this.manualOrder = manualOrder;
    }

    public Boolean getFlagged() {
        return flagged;
    }

    public void setFlagged(Boolean flagged) {
        this.flagged = flagged;
    }

    public List<String> getBlocks() {
        return blocks;
    }

    public void setBlocks(List<String> blocks) {
        this.blocks = blocks;
    }

    public List<String> getBlockedBy() {
        return blockedBy;
    }

    public void setBlockedBy(List<String> blockedBy) {
        this.blockedBy = blockedBy;
    }

    public LocalDate getExpectedDone() {
        return expectedDone;
    }

    public void setExpectedDone(LocalDate expectedDone) {
        this.expectedDone = expectedDone;
    }

    public String getAssigneeAccountId() {
        return assigneeAccountId;
    }

    public void setAssigneeAccountId(String assigneeAccountId) {
        this.assigneeAccountId = assigneeAccountId;
    }

    public String getAssigneeDisplayName() {
        return assigneeDisplayName;
    }

    public void setAssigneeDisplayName(String assigneeDisplayName) {
        this.assigneeDisplayName = assigneeDisplayName;
    }

    public String getParentProjectKey() {
        return parentProjectKey;
    }

    public void setParentProjectKey(String parentProjectKey) {
        this.parentProjectKey = parentProjectKey;
    }

    public List<DataQualityViolation> getAlerts() {
        return alerts;
    }

    public void setAlerts(List<DataQualityViolation> alerts) {
        this.alerts = alerts;
    }

    public void addAlert(DataQualityViolation alert) {
        this.alerts.add(alert);
    }

    public void addAlerts(List<DataQualityViolation> alerts) {
        this.alerts.addAll(alerts);
    }

    public List<BoardNode> getChildren() {
        return children;
    }

    public void setChildren(List<BoardNode> children) {
        this.children = children;
    }

    public void addChild(BoardNode child) {
        this.children.add(child);
    }

    public static class RoleMetrics {
        private long estimateSeconds;
        private long loggedSeconds;
        private int progress;
        private BigDecimal roughEstimateDays; // rough estimate for this role (Epic only)
        private String displayName; // human-readable role name
        private String color; // color for UI rendering

        public RoleMetrics() {
        }

        public RoleMetrics(long estimateSeconds, long loggedSeconds) {
            this.estimateSeconds = estimateSeconds;
            this.loggedSeconds = loggedSeconds;
            this.progress = estimateSeconds > 0
                ? (int) Math.min(100, (loggedSeconds * 100) / estimateSeconds)
                : 0;
        }

        public RoleMetrics(long estimateSeconds, long loggedSeconds, BigDecimal roughEstimateDays) {
            this(estimateSeconds, loggedSeconds);
            this.roughEstimateDays = roughEstimateDays;
        }

        public RoleMetrics(long estimateSeconds, long loggedSeconds, BigDecimal roughEstimateDays,
                           String displayName, String color) {
            this(estimateSeconds, loggedSeconds, roughEstimateDays);
            this.displayName = displayName;
            this.color = color;
        }

        public long getEstimateSeconds() {
            return estimateSeconds;
        }

        public void setEstimateSeconds(long estimateSeconds) {
            this.estimateSeconds = estimateSeconds;
        }

        public long getLoggedSeconds() {
            return loggedSeconds;
        }

        public void setLoggedSeconds(long loggedSeconds) {
            this.loggedSeconds = loggedSeconds;
        }

        public int getProgress() {
            return progress;
        }

        public void setProgress(int progress) {
            this.progress = progress;
        }

        public BigDecimal getRoughEstimateDays() {
            return roughEstimateDays;
        }

        public void setRoughEstimateDays(BigDecimal roughEstimateDays) {
            this.roughEstimateDays = roughEstimateDays;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }
    }
}
