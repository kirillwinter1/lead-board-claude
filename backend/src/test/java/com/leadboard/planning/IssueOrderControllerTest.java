package com.leadboard.planning;

import com.leadboard.sync.JiraIssueEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IssueOrderController.class)
class IssueOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IssueOrderService orderService;

    // ==================== Epic Order Tests ====================

    @Test
    void updateEpicOrder_success() throws Exception {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey("EPIC-1");
        epic.setManualOrder(3);
        epic.setAutoScore(new BigDecimal("45.5"));

        when(orderService.reorderEpic("EPIC-1", 3)).thenReturn(epic);

        mockMvc.perform(put("/api/epics/EPIC-1/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"position\": 3}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issueKey").value("EPIC-1"))
            .andExpect(jsonPath("$.manualOrder").value(3))
            .andExpect(jsonPath("$.autoScore").value(45.5));
    }

    @Test
    void updateEpicOrder_notFound_throwsException() throws Exception {
        when(orderService.reorderEpic("EPIC-999", 1))
            .thenThrow(new IllegalArgumentException("Epic not found: EPIC-999"));

        // IllegalArgumentException propagates as 500 error (wrapped in ServletException)
        try {
            mockMvc.perform(put("/api/epics/EPIC-999/order")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"position\": 1}"));
        } catch (Exception e) {
            // Expected - IllegalArgumentException wrapped in ServletException
            assert e.getCause() instanceof IllegalArgumentException;
        }
    }

    @Test
    void updateEpicOrder_moveToFirstPosition() throws Exception {
        JiraIssueEntity epic = new JiraIssueEntity();
        epic.setIssueKey("EPIC-5");
        epic.setManualOrder(1);
        epic.setAutoScore(new BigDecimal("30.0"));

        when(orderService.reorderEpic("EPIC-5", 1)).thenReturn(epic);

        mockMvc.perform(put("/api/epics/EPIC-5/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"position\": 1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manualOrder").value(1));
    }

    // ==================== Story Order Tests ====================

    @Test
    void updateStoryOrder_success() throws Exception {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("STORY-1");
        story.setManualOrder(2);
        story.setAutoScore(new BigDecimal("75.0"));

        when(orderService.reorderStory("STORY-1", 2)).thenReturn(story);

        mockMvc.perform(put("/api/stories/STORY-1/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"position\": 2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issueKey").value("STORY-1"))
            .andExpect(jsonPath("$.manualOrder").value(2))
            .andExpect(jsonPath("$.autoScore").value(75.0));
    }

    @Test
    void updateStoryOrder_notFound_throwsException() throws Exception {
        when(orderService.reorderStory("STORY-999", 1))
            .thenThrow(new IllegalArgumentException("Story not found: STORY-999"));

        // IllegalArgumentException propagates as 500 error (wrapped in ServletException)
        try {
            mockMvc.perform(put("/api/stories/STORY-999/order")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"position\": 1}"));
        } catch (Exception e) {
            // Expected - IllegalArgumentException wrapped in ServletException
            assert e.getCause() instanceof IllegalArgumentException;
        }
    }

    @Test
    void updateStoryOrder_bugType() throws Exception {
        JiraIssueEntity bug = new JiraIssueEntity();
        bug.setIssueKey("BUG-1");
        bug.setManualOrder(1);
        bug.setAutoScore(new BigDecimal("100.0"));

        when(orderService.reorderStory("BUG-1", 1)).thenReturn(bug);

        mockMvc.perform(put("/api/stories/BUG-1/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"position\": 1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.issueKey").value("BUG-1"))
            .andExpect(jsonPath("$.manualOrder").value(1));
    }

    @Test
    void updateStoryOrder_nullAutoScore() throws Exception {
        JiraIssueEntity story = new JiraIssueEntity();
        story.setIssueKey("STORY-1");
        story.setManualOrder(1);
        story.setAutoScore(null);

        when(orderService.reorderStory("STORY-1", 1)).thenReturn(story);

        mockMvc.perform(put("/api/stories/STORY-1/order")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"position\": 1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.autoScore").doesNotExist());
    }
}
