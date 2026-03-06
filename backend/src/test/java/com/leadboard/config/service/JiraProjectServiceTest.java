package com.leadboard.config.service;

import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.entity.JiraProjectEntity;
import com.leadboard.config.repository.JiraProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JiraProjectServiceTest {

    @Mock
    private JiraProjectRepository repository;

    @Mock
    private JiraConfigResolver jiraConfigResolver;

    private JiraProjectService service;

    @BeforeEach
    void setUp() {
        service = new JiraProjectService(repository, jiraConfigResolver);
    }

    @Test
    @DisplayName("seedFromConfig should create projects from .env when table is empty")
    void seedFromConfigCreatesProjects() {
        when(repository.count()).thenReturn(0L);
        when(jiraConfigResolver.getAllProjectKeys()).thenReturn(List.of("LB", "PROJ2"));
        when(repository.existsByProjectKey(anyString())).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.seedFromConfig();

        verify(repository, times(2)).save(any(JiraProjectEntity.class));
    }

    @Test
    @DisplayName("seedFromConfig should skip when table already has data")
    void seedFromConfigSkipsWhenNotEmpty() {
        when(repository.count()).thenReturn(2L);

        service.seedFromConfig();

        verify(jiraConfigResolver, never()).getAllProjectKeys();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("getActiveProjectKeys should return keys from DB")
    void getActiveProjectKeysFromDb() {
        JiraProjectEntity p1 = new JiraProjectEntity();
        p1.setProjectKey("LB");
        JiraProjectEntity p2 = new JiraProjectEntity();
        p2.setProjectKey("PROJ2");

        when(repository.findByActiveTrueAndSyncEnabledTrue()).thenReturn(List.of(p1, p2));

        List<String> keys = service.getActiveProjectKeys();

        assertEquals(List.of("LB", "PROJ2"), keys);
        verify(jiraConfigResolver, never()).getAllProjectKeys();
    }

    @Test
    @DisplayName("getActiveProjectKeys should fallback to .env when DB is empty")
    void getActiveProjectKeysFallsBackToEnv() {
        when(repository.findByActiveTrueAndSyncEnabledTrue()).thenReturn(List.of());
        when(jiraConfigResolver.getAllProjectKeys()).thenReturn(List.of("LB"));

        List<String> keys = service.getActiveProjectKeys();

        assertEquals(List.of("LB"), keys);
    }

    @Test
    @DisplayName("create should save new project with uppercase key")
    void createSavesNewProject() {
        when(repository.existsByProjectKey("NEWPROJ")).thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JiraProjectEntity result = service.create("newproj", "New Project");

        verify(repository).existsByProjectKey("NEWPROJ"); // checks uppercase
        ArgumentCaptor<JiraProjectEntity> captor = ArgumentCaptor.forClass(JiraProjectEntity.class);
        verify(repository).save(captor.capture());
        assertEquals("NEWPROJ", captor.getValue().getProjectKey());
        assertEquals("New Project", captor.getValue().getDisplayName());
        assertTrue(captor.getValue().isActive());
    }

    @Test
    @DisplayName("create should throw when key already exists (case-insensitive)")
    void createThrowsOnDuplicate() {
        when(repository.existsByProjectKey("LB")).thenReturn(true);

        // lowercase input should still detect existing uppercase key
        assertThrows(IllegalArgumentException.class, () -> service.create("lb", null));
    }

    @Test
    @DisplayName("update should modify existing project")
    void updateModifiesProject() {
        JiraProjectEntity existing = new JiraProjectEntity();
        existing.setProjectKey("LB");
        existing.setDisplayName("Lead Board");
        existing.setActive(true);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(1L, "New Name", false, null);

        assertEquals("New Name", existing.getDisplayName());
        assertFalse(existing.isActive());
    }

    @Test
    @DisplayName("delete should remove project by id")
    void deleteShouldRemove() {
        service.delete(1L);
        verify(repository).deleteById(1L);
    }
}
