package com.leadboard.board;

import java.util.ArrayList;
import java.util.List;

public class BoardNode {

    private String issueKey;
    private String title;
    private String status;
    private String issueType;
    private String jiraUrl;
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

    public List<BoardNode> getChildren() {
        return children;
    }

    public void setChildren(List<BoardNode> children) {
        this.children = children;
    }

    public void addChild(BoardNode child) {
        this.children.add(child);
    }
}
