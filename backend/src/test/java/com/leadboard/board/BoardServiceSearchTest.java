package com.leadboard.board;

import com.leadboard.chat.embedding.EmbeddingService;
import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.RoughEstimateProperties;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.UnifiedPlanningService;
import com.leadboard.quality.DataQualityService;
import com.leadboard.sync.JiraIssueEntity;
import com.leadboard.sync.JiraIssueRepository;
import com.leadboard.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardServiceSearchTest {

    @Mock
    private JiraIssueRepository issueRepository;

    @Mock
    private JiraConfigResolver jiraConfigResolver;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private RoughEstimateProperties roughEstimateProperties;

    @Mock
    private DataQualityService dataQualityService;

    @Mock
    private UnifiedPlanningService unifiedPlanningService;

    @Mock
    private WorkflowConfigService workflowConfigService;

    @Mock
    private EmbeddingService embeddingService;

    private BoardService boardService;

    @BeforeEach
    void setUp() throws Exception {
        boardService = new BoardService(
                issueRepository,
                jiraConfigResolver,
                teamRepository,
                roughEstimateProperties,
                dataQualityService,
                unifiedPlanningService,
                workflowConfigService
        );

        // Inject embeddingService via reflection (it's @Autowired(required=false))
        Field field = BoardService.class.getDeclaredField("embeddingService");
        field.setAccessible(true);
        field.set(boardService, embeddingService);

        when(jiraConfigResolver.getProjectKey()).thenReturn("LB");
        when(workflowConfigService.isEpic("Epic")).thenReturn(true);
        when(workflowConfigService.isStoryOrBug("Story")).thenReturn(true);
        when(workflowConfigService.isEpic("Story")).thenReturn(false);
        when(workflowConfigService.isStoryOrBug("Epic")).thenReturn(false);
        when(workflowConfigService.isEpic("Sub-task")).thenReturn(false);
        when(workflowConfigService.isStoryOrBug("Sub-task")).thenReturn(false);
    }

    private JiraIssueEntity createIssue(String key, String type, String summary, String parentKey, Long teamId) {
        JiraIssueEntity entity = new JiraIssueEntity();
        entity.setIssueKey(key);
        entity.setIssueType(type);
        entity.setSummary(summary);
        entity.setParentKey(parentKey);
        entity.setTeamId(teamId);
        entity.setSubtask("Sub-task".equals(type));
        entity.setBoardCategory(
                "Epic".equals(type) ? "EPIC" :
                "Story".equals(type) ? "STORY" : null
        );
        return entity;
    }

    @Test
    @DisplayName("Semantic search returns epic → epic key in result")
    void semanticSearchReturnsEpicDirectly() {
        JiraIssueEntity epic = createIssue("LB-1", "Epic", "User authentication", null, 1L);
        when(embeddingService.search(eq("authentication"), isNull(), eq(30)))
                .thenReturn(List.of(epic));

        BoardSearchResponse result = boardService.searchForBoard("authentication", null);

        assertEquals("semantic", result.searchMode());
        assertTrue(result.matchedEpicKeys().contains("LB-1"));
    }

    @Test
    @DisplayName("Semantic search returns story → parent epic key in result")
    void semanticSearchReturnsStoryResolvesToParent() {
        JiraIssueEntity story = createIssue("LB-10", "Story", "Login form", "LB-1", 1L);
        when(embeddingService.search(eq("login form"), isNull(), eq(30)))
                .thenReturn(List.of(story));

        BoardSearchResponse result = boardService.searchForBoard("login form", null);

        assertEquals("semantic", result.searchMode());
        assertTrue(result.matchedEpicKeys().contains("LB-1"));
    }

    @Test
    @DisplayName("Semantic search returns subtask → grandparent epic key")
    void semanticSearchSubtaskResolvesToGrandparent() {
        JiraIssueEntity subtask = createIssue("LB-100", "Sub-task", "Write tests", "LB-10", 1L);
        JiraIssueEntity parentStory = createIssue("LB-10", "Story", "Login form", "LB-1", 1L);

        when(embeddingService.search(eq("write tests"), isNull(), eq(30)))
                .thenReturn(List.of(subtask));
        when(issueRepository.findByIssueKey("LB-10")).thenReturn(Optional.of(parentStory));

        BoardSearchResponse result = boardService.searchForBoard("write tests", null);

        assertEquals("semantic", result.searchMode());
        assertTrue(result.matchedEpicKeys().contains("LB-1"));
    }

    @Test
    @DisplayName("Semantic search empty → fallback to substring")
    void fallbackToSubstringWhenSemanticEmpty() {
        when(embeddingService.search(anyString(), isNull(), eq(30)))
                .thenReturn(Collections.emptyList());

        JiraIssueEntity epic = createIssue("LB-1", "Epic", "Payment integration", null, 1L);
        when(issueRepository.findByProjectKeyAndBoardCategory("LB", "EPIC"))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKeyIn(List.of("LB-1")))
                .thenReturn(Collections.emptyList());

        BoardSearchResponse result = boardService.searchForBoard("payment", null);

        assertEquals("substring", result.searchMode());
        assertTrue(result.matchedEpicKeys().contains("LB-1"));
    }

    @Test
    @DisplayName("Substring search matches story summary → returns parent epic")
    void substringSearchMatchesStory() {
        when(embeddingService.search(anyString(), isNull(), eq(30)))
                .thenReturn(Collections.emptyList());

        JiraIssueEntity epic = createIssue("LB-1", "Epic", "Auth module", null, 1L);
        JiraIssueEntity story = createIssue("LB-10", "Story", "Password reset flow", "LB-1", 1L);

        when(issueRepository.findByProjectKeyAndBoardCategory("LB", "EPIC"))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKeyIn(List.of("LB-1")))
                .thenReturn(List.of(story));

        BoardSearchResponse result = boardService.searchForBoard("password reset", null);

        assertEquals("substring", result.searchMode());
        assertTrue(result.matchedEpicKeys().contains("LB-1"));
    }

    @Test
    @DisplayName("Team filtering works with semantic search")
    void teamFilteringWithSemanticSearch() {
        JiraIssueEntity epic1 = createIssue("LB-1", "Epic", "Feature A", null, 1L);
        JiraIssueEntity epic2 = createIssue("LB-2", "Epic", "Feature B", null, 2L);

        when(embeddingService.search(eq("feature"), eq(1L), eq(30)))
                .thenReturn(List.of(epic1, epic2));

        BoardSearchResponse result = boardService.searchForBoard("feature", List.of(1L));

        assertEquals("semantic", result.searchMode());
        assertTrue(result.matchedEpicKeys().contains("LB-1"));
        assertFalse(result.matchedEpicKeys().contains("LB-2"));
    }

    @Test
    @DisplayName("No embedding service → substring fallback")
    void noEmbeddingServiceFallsBack() throws Exception {
        // Remove embeddingService
        Field field = BoardService.class.getDeclaredField("embeddingService");
        field.setAccessible(true);
        field.set(boardService, null);

        JiraIssueEntity epic = createIssue("LB-1", "Epic", "Search feature", null, 1L);
        when(issueRepository.findByProjectKeyAndBoardCategory("LB", "EPIC"))
                .thenReturn(List.of(epic));
        when(issueRepository.findByParentKeyIn(List.of("LB-1")))
                .thenReturn(Collections.emptyList());

        BoardSearchResponse result = boardService.searchForBoard("search", null);

        assertEquals("substring", result.searchMode());
        assertTrue(result.matchedEpicKeys().contains("LB-1"));
    }

    @Test
    @DisplayName("Empty project key returns empty result")
    void emptyProjectKeyReturnsEmpty() {
        when(jiraConfigResolver.getProjectKey()).thenReturn("");

        BoardSearchResponse result = boardService.searchForBoard("test", null);

        assertTrue(result.matchedEpicKeys().isEmpty());
    }
}
