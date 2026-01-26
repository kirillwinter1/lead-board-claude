package com.leadboard.controller;

import com.leadboard.config.JiraProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConfigController {

    private final JiraProperties jiraProperties;

    public ConfigController(JiraProperties jiraProperties) {
        this.jiraProperties = jiraProperties;
    }

    @GetMapping("/config")
    public Map<String, String> getConfig() {
        String baseUrl = jiraProperties.getBaseUrl();
        String jiraBrowseUrl = (baseUrl != null && !baseUrl.isEmpty())
            ? baseUrl + "/browse/"
            : "";
        return Map.of("jiraBaseUrl", jiraBrowseUrl);
    }
}
