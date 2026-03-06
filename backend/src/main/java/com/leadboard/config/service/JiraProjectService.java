package com.leadboard.config.service;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.entity.JiraProjectEntity;
import com.leadboard.config.repository.JiraProjectRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JiraProjectService {

    private static final Logger log = LoggerFactory.getLogger(JiraProjectService.class);

    private final JiraProjectRepository repository;
    private final JiraConfigResolver jiraConfigResolver;

    public JiraProjectService(JiraProjectRepository repository,
                              JiraConfigResolver jiraConfigResolver) {
        this.repository = repository;
        this.jiraConfigResolver = jiraConfigResolver;
    }

    /**
     * Auto-seed from .env/tenant config on startup if table is empty.
     */
    @PostConstruct
    public void seedFromConfig() {
        try {
            if (repository.count() > 0) {
                return;
            }
            List<String> keys = jiraConfigResolver.getAllProjectKeys();
            for (String key : keys) {
                if (!repository.existsByProjectKey(key)) {
                    JiraProjectEntity entity = new JiraProjectEntity();
                    entity.setProjectKey(key);
                    entity.setDisplayName(key);
                    entity.setActive(true);
                    entity.setSyncEnabled(true);
                    repository.save(entity);
                    log.info("Auto-seeded project: {}", key);
                }
            }
        } catch (Exception e) {
            log.warn("Could not auto-seed jira_projects (table may not exist yet): {}", e.getMessage());
        }
    }

    public List<JiraProjectEntity> listAll() {
        return repository.findAll();
    }

    public List<JiraProjectEntity> listActive() {
        return repository.findByActiveTrue();
    }

    public List<String> getActiveProjectKeys() {
        try {
            List<JiraProjectEntity> active = repository.findByActiveTrueAndSyncEnabledTrue();
            if (!active.isEmpty()) {
                return active.stream().map(JiraProjectEntity::getProjectKey).toList();
            }
        } catch (Exception e) {
            log.debug("Could not read jira_projects: {}", e.getMessage());
        }
        // Fallback to .env / tenant config
        return jiraConfigResolver.getAllProjectKeys();
    }

    public JiraProjectEntity create(String projectKey, String displayName) {
        String normalizedKey = projectKey.toUpperCase().trim();
        if (repository.existsByProjectKey(normalizedKey)) {
            throw new IllegalArgumentException("Project key already exists: " + normalizedKey);
        }
        JiraProjectEntity entity = new JiraProjectEntity();
        entity.setProjectKey(normalizedKey);
        entity.setDisplayName(displayName != null ? displayName.trim() : projectKey.toUpperCase().trim());
        entity.setActive(true);
        entity.setSyncEnabled(true);
        return repository.save(entity);
    }

    public JiraProjectEntity update(Long id, String displayName, Boolean active, Boolean syncEnabled) {
        JiraProjectEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
        if (displayName != null) entity.setDisplayName(displayName.trim());
        if (active != null) entity.setActive(active);
        if (syncEnabled != null) entity.setSyncEnabled(syncEnabled);
        return repository.save(entity);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }
}
