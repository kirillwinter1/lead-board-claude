package com.leadboard.jira;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssue {

    private String id;
    private String key;
    private String self;
    private JiraFields fields;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getSelf() {
        return self;
    }

    public void setSelf(String self) {
        this.self = self;
    }

    public JiraFields getFields() {
        return fields;
    }

    public void setFields(JiraFields fields) {
        this.fields = fields;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraFields {
        private String summary;
        private JiraStatus status;
        private JiraIssueType issuetype;
        private JiraParent parent;
        private JiraProject project;
        private JiraTimeTracking timetracking;
        private JiraPriority priority;
        private String duedate;
        private String created;
        private JiraUser assignee;
        private List<Object> flagged; // Jira field for Impediment flag
        private List<JiraIssueLink> issuelinks;
        private Map<String, Object> customFields = new HashMap<>();

        @JsonAnySetter
        public void setCustomField(String key, Object value) {
            if (key.startsWith("customfield_")) {
                customFields.put(key, value);
            }
        }

        public Map<String, Object> getCustomFields() {
            return customFields;
        }

        public Object getCustomField(String fieldId) {
            return customFields.get(fieldId);
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public JiraStatus getStatus() {
            return status;
        }

        public void setStatus(JiraStatus status) {
            this.status = status;
        }

        public JiraIssueType getIssuetype() {
            return issuetype;
        }

        public void setIssuetype(JiraIssueType issuetype) {
            this.issuetype = issuetype;
        }

        public JiraParent getParent() {
            return parent;
        }

        public void setParent(JiraParent parent) {
            this.parent = parent;
        }

        public JiraProject getProject() {
            return project;
        }

        public void setProject(JiraProject project) {
            this.project = project;
        }

        public JiraTimeTracking getTimetracking() {
            return timetracking;
        }

        public void setTimetracking(JiraTimeTracking timetracking) {
            this.timetracking = timetracking;
        }

        public JiraPriority getPriority() {
            return priority;
        }

        public void setPriority(JiraPriority priority) {
            this.priority = priority;
        }

        public String getDuedate() {
            return duedate;
        }

        public void setDuedate(String duedate) {
            this.duedate = duedate;
        }

        public String getCreated() {
            return created;
        }

        public void setCreated(String created) {
            this.created = created;
        }

        public List<Object> getFlagged() {
            return flagged;
        }

        public void setFlagged(List<Object> flagged) {
            this.flagged = flagged;
        }

        public List<JiraIssueLink> getIssuelinks() {
            return issuelinks;
        }

        public void setIssuelinks(List<JiraIssueLink> issuelinks) {
            this.issuelinks = issuelinks;
        }

        public JiraUser getAssignee() {
            return assignee;
        }

        public void setAssignee(JiraUser assignee) {
            this.assignee = assignee;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraTimeTracking {
        private Long originalEstimateSeconds;
        private Long timeSpentSeconds;

        public Long getOriginalEstimateSeconds() {
            return originalEstimateSeconds;
        }

        public void setOriginalEstimateSeconds(Long originalEstimateSeconds) {
            this.originalEstimateSeconds = originalEstimateSeconds;
        }

        public Long getTimeSpentSeconds() {
            return timeSpentSeconds;
        }

        public void setTimeSpentSeconds(Long timeSpentSeconds) {
            this.timeSpentSeconds = timeSpentSeconds;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraStatus {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraIssueType {
        private String name;
        private boolean subtask;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isSubtask() {
            return subtask;
        }

        public void setSubtask(boolean subtask) {
            this.subtask = subtask;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraParent {
        private String id;
        private String key;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraProject {
        private String key;
        private String name;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraPriority {
        private String name;
        private String id;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraIssueLink {
        private String id;
        private JiraIssueLinkType type;
        private JiraLinkedIssue outwardIssue;
        private JiraLinkedIssue inwardIssue;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public JiraIssueLinkType getType() {
            return type;
        }

        public void setType(JiraIssueLinkType type) {
            this.type = type;
        }

        public JiraLinkedIssue getOutwardIssue() {
            return outwardIssue;
        }

        public void setOutwardIssue(JiraLinkedIssue outwardIssue) {
            this.outwardIssue = outwardIssue;
        }

        public JiraLinkedIssue getInwardIssue() {
            return inwardIssue;
        }

        public void setInwardIssue(JiraLinkedIssue inwardIssue) {
            this.inwardIssue = inwardIssue;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraIssueLinkType {
        private String name;
        private String inward;  // e.g., "is blocked by"
        private String outward; // e.g., "blocks"

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getInward() {
            return inward;
        }

        public void setInward(String inward) {
            this.inward = inward;
        }

        public String getOutward() {
            return outward;
        }

        public void setOutward(String outward) {
            this.outward = outward;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraLinkedIssue {
        private String id;
        private String key;
        private JiraLinkedIssueFields fields;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public JiraLinkedIssueFields getFields() {
            return fields;
        }

        public void setFields(JiraLinkedIssueFields fields) {
            this.fields = fields;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraLinkedIssueFields {
        private String summary;
        private JiraStatus status;

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public JiraStatus getStatus() {
            return status;
        }

        public void setStatus(JiraStatus status) {
            this.status = status;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JiraUser {
        private String accountId;
        private String displayName;
        private String emailAddress;

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getEmailAddress() {
            return emailAddress;
        }

        public void setEmailAddress(String emailAddress) {
            this.emailAddress = emailAddress;
        }
    }
}
