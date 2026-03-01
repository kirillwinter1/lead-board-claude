package com.leadboard.chat;

import com.leadboard.auth.AuthorizationService;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.chat.dto.ChatSseEvent;
import com.leadboard.chat.llm.*;
import com.leadboard.chat.tools.ChatToolExecutor;
import com.leadboard.chat.tools.ChatToolRegistry;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatProperties chatProperties;
    private final LlmClient llmClient;
    private final ChatToolRegistry toolRegistry;
    private final ChatToolExecutor toolExecutor;
    private final AuthorizationService authorizationService;
    private final TeamRepository teamRepository;

    private final Map<String, List<LlmMessage>> sessions = new ConcurrentHashMap<>();
    private String knowledgeBase = "";

    public ChatService(
            ChatProperties chatProperties,
            LlmClient llmClient,
            ChatToolRegistry toolRegistry,
            ChatToolExecutor toolExecutor,
            AuthorizationService authorizationService,
            TeamRepository teamRepository
    ) {
        this.chatProperties = chatProperties;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.toolExecutor = toolExecutor;
        this.authorizationService = authorizationService;
        this.teamRepository = teamRepository;
    }

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("chat/knowledge_base.md");
            knowledgeBase = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("Loaded chat knowledge base: {} chars", knowledgeBase.length());
        } catch (IOException e) {
            log.warn("Failed to load knowledge base: {}", e.getMessage());
        }
    }

    public Flux<ChatSseEvent> processMessage(String sessionId, String userMessage) {
        if (!chatProperties.isEnabled()) {
            return Flux.just(ChatSseEvent.error("Chat is not enabled", sessionId));
        }

        return Flux.create(sink -> {
            try {
                List<LlmMessage> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());

                // Build system prompt
                LlmMessage systemPrompt = buildSystemPrompt();

                // Add user message
                history.add(LlmMessage.user(userMessage));

                // Trim history if too long
                trimHistory(history);

                // Tool loop
                List<LlmMessage> fullMessages = new ArrayList<>();
                fullMessages.add(systemPrompt);
                fullMessages.addAll(history);

                List<LlmToolDefinition> tools = toolRegistry.getToolDefinitions();
                int toolCallCount = 0;

                while (toolCallCount < chatProperties.getMaxToolCalls()) {
                    LlmResponse response = llmClient.chat(fullMessages, tools);

                    if (!response.hasToolCalls()) {
                        // No more tool calls — add assistant message and stream the final response
                        if (response.content() != null) {
                            history.add(LlmMessage.assistant(response.content()));
                        }
                        break;
                    }

                    // Process tool calls
                    LlmMessage assistantMsg = LlmMessage.assistantWithToolCalls(response.toolCalls());
                    history.add(assistantMsg);
                    fullMessages.add(assistantMsg);

                    for (LlmToolCall toolCall : response.toolCalls()) {
                        // Emit tool_call event for UI
                        sink.next(ChatSseEvent.toolCall(toolCall.functionName(), sessionId));

                        String result = toolExecutor.executeTool(toolCall.functionName(), toolCall.argumentsJson());
                        LlmMessage toolResultMsg = LlmMessage.toolResult(toolCall.id(), toolCall.functionName(), result);
                        history.add(toolResultMsg);
                        fullMessages.add(toolResultMsg);
                    }

                    toolCallCount++;
                }

                // Stream final response
                List<LlmMessage> streamMessages = new ArrayList<>();
                streamMessages.add(systemPrompt);
                streamMessages.addAll(history);

                // Check if we already have a final text response from the tool loop
                LlmMessage lastMsg = history.get(history.size() - 1);
                if ("assistant".equals(lastMsg.role()) && lastMsg.content() != null) {
                    // We already have the final text, emit it as a single chunk
                    sink.next(ChatSseEvent.text(lastMsg.content(), sessionId));
                    sink.next(ChatSseEvent.done(sessionId));
                    sink.complete();
                    return;
                }

                // Need to stream the final response
                StringBuilder fullResponse = new StringBuilder();
                llmClient.streamChat(streamMessages)
                        .doOnNext(chunk -> {
                            fullResponse.append(chunk);
                            sink.next(ChatSseEvent.text(chunk, sessionId));
                        })
                        .doOnComplete(() -> {
                            if (!fullResponse.isEmpty()) {
                                history.add(LlmMessage.assistant(fullResponse.toString()));
                            }
                            sink.next(ChatSseEvent.done(sessionId));
                            sink.complete();
                        })
                        .doOnError(error -> {
                            log.error("Stream error: {}", error.getMessage());
                            sink.next(ChatSseEvent.error("Stream error: " + error.getMessage(), sessionId));
                            sink.complete();
                        })
                        .subscribe();

            } catch (Exception e) {
                log.error("Chat processing error: {}", e.getMessage(), e);
                sink.next(ChatSseEvent.error("Error: " + e.getMessage(), sessionId));
                sink.complete();
            }
        });
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
    }

    private LlmMessage buildSystemPrompt() {
        LeadBoardAuthentication auth = authorizationService.getCurrentAuth();
        String role = auth != null ? auth.getRole().name() : "VIEWER";
        String userName = auth != null ? auth.getName() : "Unknown";

        Set<Long> userTeamIds = authorizationService.getUserTeamIds();
        String teamInfo = "";
        if (!userTeamIds.isEmpty()) {
            List<TeamEntity> teams = teamRepository.findByActiveTrue();
            teamInfo = teams.stream()
                    .filter(t -> userTeamIds.contains(t.getId()))
                    .map(t -> t.getName() + " (id=" + t.getId() + ")")
                    .collect(Collectors.joining(", "));
        }

        String prompt = """
                Ты — AI-ассистент системы LeadBoard. Отвечай на русском языке, кратко и по делу.

                Роль пользователя: %s
                Имя пользователя: %s
                Команды пользователя: %s

                ПРАВИЛА:
                1. Используй инструменты (tools) для получения актуальных данных. Не выдумывай числа.
                2. Отвечай на вопросы о навигации, метриках, задачах на основе базы знаний ниже.
                3. НЕ раскрывай внутренние алгоритмы (AutoScore формулы, веса факторов).
                4. Если пользователь TEAM_LEAD или MEMBER — показывай данные только его команд.
                5. Форматируй ответы с markdown: жирный текст, списки, таблицы где уместно.
                6. Если не знаешь ответ — честно скажи об этом.

                БАЗА ЗНАНИЙ:
                %s
                """.formatted(role, userName, teamInfo.isEmpty() ? "не назначены" : teamInfo, knowledgeBase);

        return LlmMessage.system(prompt);
    }

    private void trimHistory(List<LlmMessage> history) {
        int max = chatProperties.getMaxHistoryMessages();
        while (history.size() > max) {
            history.remove(0);
        }
    }

    // Visible for testing
    String getKnowledgeBase() {
        return knowledgeBase;
    }

    // Visible for testing
    void setKnowledgeBase(String kb) {
        this.knowledgeBase = kb;
    }

    // Visible for testing
    Map<String, List<LlmMessage>> getSessions() {
        return sessions;
    }
}
