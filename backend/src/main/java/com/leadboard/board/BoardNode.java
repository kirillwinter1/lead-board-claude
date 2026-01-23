package com.leadboard.board;

import java.util.ArrayList;
import java.util.List;

public class BoardNode {

    private String issueKey;
    private String title;
    private String status;
    private String issueType;
    private String jiraUrl;
    private String role; // ANALYTICS, DEVELOPMENT, TESTING (for sub-tasks)
    private Long teamId;
    private String teamName;
    private Long estimateSeconds;
    private Long loggedSeconds;
    private Integer progress; // 0-100
    private RoleProgress roleProgress; // aggregated progress by role
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

    public RoleProgress getRoleProgress() {
        return roleProgress;
    }

    public void setRoleProgress(RoleProgress roleProgress) {
        this.roleProgress = roleProgress;
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

    public static class RoleProgress {
        private RoleMetrics analytics;
        private RoleMetrics development;
        private RoleMetrics testing;

        public RoleProgress() {
            this.analytics = new RoleMetrics();
            this.development = new RoleMetrics();
            this.testing = new RoleMetrics();
        }

        public RoleMetrics getAnalytics() {
            return analytics;
        }

        public void setAnalytics(RoleMetrics analytics) {
            this.analytics = analytics;
        }

        public RoleMetrics getDevelopment() {
            return development;
        }

        public void setDevelopment(RoleMetrics development) {
            this.development = development;
        }

        public RoleMetrics getTesting() {
            return testing;
        }

        public void setTesting(RoleMetrics testing) {
            this.testing = testing;
        }
    }

    public static class RoleMetrics {
        private long estimateSeconds;
        private long loggedSeconds;
        private int progress;

        public RoleMetrics() {
        }

        public RoleMetrics(long estimateSeconds, long loggedSeconds) {
            this.estimateSeconds = estimateSeconds;
            this.loggedSeconds = loggedSeconds;
            this.progress = estimateSeconds > 0
                ? (int) Math.min(100, (loggedSeconds * 100) / estimateSeconds)
                : 0;
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
    }
}
