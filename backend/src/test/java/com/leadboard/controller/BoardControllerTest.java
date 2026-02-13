package com.leadboard.controller;

import com.leadboard.board.BoardNode;
import com.leadboard.board.BoardResponse;
import com.leadboard.board.BoardService;
import com.leadboard.config.service.WorkflowConfigService;
import com.leadboard.planning.*;
import com.leadboard.status.StatusMappingService;
import com.leadboard.sync.JiraIssueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import com.leadboard.auth.SessionRepository;
import com.leadboard.config.AppProperties;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BoardController.class)
@AutoConfigureMockMvc(addFilters = false)
class BoardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // BoardController dependencies
    @MockBean
    private BoardService boardService;
    @MockBean
    private AutoScoreService autoScoreService;
    @MockBean
    private StoryAutoScoreService storyAutoScoreService;
    @MockBean
    private StatusMappingService statusMappingService;
    @MockBean
    private WorkflowConfigService workflowConfigService;
    @MockBean
    private JiraIssueRepository jiraIssueRepository;
    @MockBean
    private SessionRepository sessionRepository;
    @MockBean
    private AppProperties appProperties;

    // ForecastController dependencies (loaded by WebMvcTest)
    @MockBean
    private ForecastService forecastService;
    @MockBean
    private StoryForecastService storyForecastService;
    @MockBean
    private UnifiedPlanningService unifiedPlanningService;
    @MockBean
    private WipSnapshotService wipSnapshotService;

    @Test
    void getBoardReturnsEmptyListWhenNoEpics() throws Exception {
        when(boardService.getBoard(isNull(), isNull(), isNull(), eq(0), eq(50)))
                .thenReturn(new BoardResponse(List.of(), 0));

        mockMvc.perform(get("/api/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void getBoardReturnsEpicsWithStories() throws Exception {
        BoardNode story = new BoardNode("PROJ-2", "Implement feature", "In Progress", "Story", "https://jira.example.com/browse/PROJ-2");
        BoardNode epic = new BoardNode("PROJ-1", "Epic title", "To Do", "Epic", "https://jira.example.com/browse/PROJ-1");
        epic.addChild(story);

        when(boardService.getBoard(isNull(), isNull(), isNull(), eq(0), eq(50)))
                .thenReturn(new BoardResponse(List.of(epic), 1));

        mockMvc.perform(get("/api/board"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].issueKey").value("PROJ-1"))
                .andExpect(jsonPath("$.items[0].title").value("Epic title"))
                .andExpect(jsonPath("$.items[0].issueType").value("Epic"))
                .andExpect(jsonPath("$.items[0].children[0].issueKey").value("PROJ-2"))
                .andExpect(jsonPath("$.items[0].children[0].title").value("Implement feature"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getBoardWithQueryFilter() throws Exception {
        BoardNode epic = new BoardNode("PROJ-1", "Search result", "To Do", "Epic", "https://jira.example.com/browse/PROJ-1");

        when(boardService.getBoard(eq("search"), isNull(), isNull(), eq(0), eq(50)))
                .thenReturn(new BoardResponse(List.of(epic), 1));

        mockMvc.perform(get("/api/board").param("query", "search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].issueKey").value("PROJ-1"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getBoardWithStatusFilter() throws Exception {
        BoardNode epic = new BoardNode("PROJ-1", "In Progress Epic", "In Progress", "Epic", "https://jira.example.com/browse/PROJ-1");

        when(boardService.getBoard(isNull(), eq(List.of("In Progress")), isNull(), eq(0), eq(50)))
                .thenReturn(new BoardResponse(List.of(epic), 1));

        mockMvc.perform(get("/api/board").param("statuses", "In Progress"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("In Progress"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getBoardWithTeamFilter() throws Exception {
        BoardNode epic = new BoardNode("PROJ-1", "Team Epic", "In Progress", "Epic", "https://jira.example.com/browse/PROJ-1");
        epic.setTeamId(1L);
        epic.setTeamName("Team A");

        when(boardService.getBoard(isNull(), isNull(), eq(List.of(1L)), eq(0), eq(50)))
                .thenReturn(new BoardResponse(List.of(epic), 1));

        mockMvc.perform(get("/api/board").param("teamIds", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].teamId").value(1))
                .andExpect(jsonPath("$.items[0].teamName").value("Team A"))
                .andExpect(jsonPath("$.total").value(1));
    }
}
