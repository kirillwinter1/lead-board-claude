package com.leadboard.chat;

import com.leadboard.auth.AppRole;
import com.leadboard.auth.AuthorizationService;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.chat.dto.ChatSseEvent;
import com.leadboard.chat.llm.*;
import com.leadboard.chat.tools.ChatToolExecutor;
import com.leadboard.chat.tools.ChatToolRegistry;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatServiceTest {

    @Mock private ChatProperties chatProperties;
    @Mock private LlmClient llmClient;
    @Mock private ChatToolRegistry toolRegistry;
    @Mock private ChatToolExecutor toolExecutor;
    @Mock private AuthorizationService authorizationService;
    @Mock private TeamRepository teamRepository;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        when(chatProperties.isEnabled()).thenReturn(true);
        when(chatProperties.getMaxHistoryMessages()).thenReturn(20);
        when(chatProperties.getMaxToolCalls()).thenReturn(5);
        when(toolRegistry.getToolDefinitions()).thenReturn(List.of());

        LeadBoardAuthentication auth = mock(LeadBoardAuthentication.class);
        when(auth.getRole()).thenReturn(AppRole.ADMIN);
        when(auth.getName()).thenReturn("Test User");
        when(authorizationService.getCurrentAuth()).thenReturn(auth);
        when(authorizationService.getUserTeamIds()).thenReturn(Set.of(1L));

        TeamEntity team = new TeamEntity();
        team.setId(1L);
        team.setName("Dev Team");
        when(teamRepository.findByActiveTrue()).thenReturn(List.of(team));

        chatService = new ChatService(chatProperties, llmClient, toolRegistry,
                toolExecutor, authorizationService, teamRepository);
        chatService.setKnowledgeBase("Test knowledge base content");
    }

    @Test
    @DisplayName("Chat disabled returns error event")
    void chatDisabledReturnsError() {
        when(chatProperties.isEnabled()).thenReturn(false);

        List<ChatSseEvent> events = chatService.processMessage("session1", "Hello")
                .collectList().block();

        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("error", events.get(0).type());
        assertTrue(events.get(0).content().contains("not enabled"));
    }

    @Test
    @DisplayName("Simple message without tool calls streams response")
    void simpleMessageStreamsResponse() {
        LlmResponse response = new LlmResponse("Hello! How can I help?", null, "stop");
        when(llmClient.chat(anyList(), anyList())).thenReturn(response);

        List<ChatSseEvent> events = chatService.processMessage("session1", "Hi")
                .collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> "text".equals(e.type())));
        assertTrue(events.stream().anyMatch(e -> "done".equals(e.type())));
    }

    @Test
    @DisplayName("Tool loop executes tools and returns result")
    void toolLoopExecutesTools() {
        // First call returns tool calls
        LlmToolCall toolCall = new LlmToolCall("tc1", "team_list", "{}");
        LlmResponse toolResponse = new LlmResponse(null, List.of(toolCall), "tool_calls");
        // Second call returns text
        LlmResponse textResponse = new LlmResponse("There are 2 teams.", null, "stop");

        when(llmClient.chat(anyList(), anyList()))
                .thenReturn(toolResponse)
                .thenReturn(textResponse);

        when(toolExecutor.executeTool("team_list", "{}")).thenReturn("{\"teams\":[]}");

        List<ChatSseEvent> events = chatService.processMessage("session1", "List teams")
                .collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e -> "tool_call".equals(e.type())));
        verify(toolExecutor).executeTool("team_list", "{}");
    }

    @Test
    @DisplayName("Session clear removes history")
    void sessionClearRemovesHistory() {
        LlmResponse response = new LlmResponse("Hello!", null, "stop");
        when(llmClient.chat(anyList(), anyList())).thenReturn(response);

        chatService.processMessage("session1", "Hi").collectList().block();
        assertFalse(chatService.getSessions().isEmpty());

        chatService.clearSession("session1");
        assertFalse(chatService.getSessions().containsKey("session1"));
    }

    @Test
    @DisplayName("History trimming keeps within max limit")
    void historyTrimmingWorks() {
        when(chatProperties.getMaxHistoryMessages()).thenReturn(4);
        LlmResponse response = new LlmResponse("Reply", null, "stop");
        when(llmClient.chat(anyList(), anyList())).thenReturn(response);

        // Send multiple messages to exceed max (each round adds user + assistant = 2 messages)
        chatService.processMessage("session1", "msg1").collectList().block();
        chatService.processMessage("session1", "msg2").collectList().block();
        chatService.processMessage("session1", "msg3").collectList().block();
        chatService.processMessage("session1", "msg4").collectList().block();

        List<LlmMessage> history = chatService.getSessions().get("session1");
        assertNotNull(history);
        // After trimming, history should not exceed maxHistoryMessages
        assertTrue(history.size() <= 5, "History size " + history.size() + " should be trimmed");
    }

    @Test
    @DisplayName("Tool loop respects maxToolCalls limit")
    void toolLoopRespectsLimit() {
        when(chatProperties.getMaxToolCalls()).thenReturn(2);

        LlmToolCall toolCall = new LlmToolCall("tc1", "team_list", "{}");
        LlmResponse toolResponse = new LlmResponse(null, List.of(toolCall), "tool_calls");

        // Always return tool calls (never text) to test the limit
        when(llmClient.chat(anyList(), anyList())).thenReturn(toolResponse);
        when(toolExecutor.executeTool(anyString(), anyString())).thenReturn("{}");

        // Stream the final response since tool loop finished without text
        when(llmClient.streamChat(anyList())).thenReturn(Flux.just("Done"));

        List<ChatSseEvent> events = chatService.processMessage("session1", "Loop test")
                .collectList().block();

        // Should have called chat at most maxToolCalls + the initial = 2 times for tools
        verify(llmClient, atMost(3)).chat(anyList(), anyList());
    }
}
