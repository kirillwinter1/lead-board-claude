package com.leadboard.controller;

import com.leadboard.config.JiraConfigResolver;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ConfigController {

    private final JiraConfigResolver jiraConfigResolver;

    public ConfigController(JiraConfigResolver jiraConfigResolver) {
        this.jiraConfigResolver = jiraConfigResolver;
    }

    @GetMapping("/config")
    public Map<String, String> getConfig() {
        String baseUrl = jiraConfigResolver.getBaseUrl();
        String jiraBrowseUrl = (baseUrl != null && !baseUrl.isEmpty())
            ? baseUrl + "/browse/"
            : "";
        return Map.of("jiraBaseUrl", jiraBrowseUrl);
    }
}
