package com.leadboard.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO for parsing Jira worklog response.
 * Maps GET /rest/api/3/issue/{key}/worklog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraWorklogResponse {

    private int startAt;
    private int maxResults;
    private int total;
    private List<WorklogEntry> worklogs;

    public int getStartAt() { return startAt; }
    public void setStartAt(int startAt) { this.startAt = startAt; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public List<WorklogEntry> getWorklogs() { return worklogs; }
    public void setWorklogs(List<WorklogEntry> worklogs) { this.worklogs = worklogs; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorklogEntry {
        private String id;
        private Author author;
        private int timeSpentSeconds;
        private String started; // ISO datetime string e.g. "2024-01-15T10:00:00.000+0000"

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public Author getAuthor() { return author; }
        public void setAuthor(Author author) { this.author = author; }

        public int getTimeSpentSeconds() { return timeSpentSeconds; }
        public void setTimeSpentSeconds(int timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }

        public String getStarted() { return started; }
        public void setStarted(String started) { this.started = started; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Author {
        private String accountId;
        private String displayName;

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
}
