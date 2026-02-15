package com.leadboard.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DTO for parsing Jira issue response with expand=changelog.
 * Maps the changelog section of GET /rest/api/3/issue/{key}?expand=changelog
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraChangelogResponse {

    private String id;
    private String key;
    private Changelog changelog;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public Changelog getChangelog() { return changelog; }
    public void setChangelog(Changelog changelog) { this.changelog = changelog; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Changelog {
        private int startAt;
        private int maxResults;
        private int total;
        private List<ChangelogHistory> histories;

        public int getStartAt() { return startAt; }
        public void setStartAt(int startAt) { this.startAt = startAt; }

        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public List<ChangelogHistory> getHistories() { return histories; }
        public void setHistories(List<ChangelogHistory> histories) { this.histories = histories; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChangelogHistory {
        private String id;
        private String created;
        private List<ChangelogItem> items;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getCreated() { return created; }
        public void setCreated(String created) { this.created = created; }

        public List<ChangelogItem> getItems() { return items; }
        public void setItems(List<ChangelogItem> items) { this.items = items; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChangelogItem {
        private String field;
        private String fieldtype;
        private String fromString;
        private String toString;

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }

        public String getFieldtype() { return fieldtype; }
        public void setFieldtype(String fieldtype) { this.fieldtype = fieldtype; }

        public String getFromString() { return fromString; }
        public void setFromString(String fromString) { this.fromString = fromString; }

        public String getToString() { return toString; }
        public void setToString(String toString) { this.toString = toString; }
    }

    /**
     * DTO for paginated changelog endpoint: GET /rest/api/3/issue/{key}/changelog
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaginatedChangelog {
        private int startAt;
        private int maxResults;
        private int total;
        private boolean isLast;
        private List<ChangelogHistory> values;

        public int getStartAt() { return startAt; }
        public void setStartAt(int startAt) { this.startAt = startAt; }

        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public boolean isLast() { return isLast; }
        public void setLast(boolean last) { isLast = last; }

        public List<ChangelogHistory> getValues() { return values; }
        public void setValues(List<ChangelogHistory> values) { this.values = values; }
    }
}
