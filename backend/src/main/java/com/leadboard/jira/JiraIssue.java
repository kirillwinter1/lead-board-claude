package com.leadboard.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
}
