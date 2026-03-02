package com.leadboard.chat;

import com.leadboard.auth.AuthorizationService;
import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.chat.dto.ChatSseEvent;
import com.leadboard.chat.llm.*;
import com.leadboard.chat.tools.ChatToolExecutor;
import com.leadboard.chat.tools.ChatToolRegistry;
import com.leadboard.team.TeamEntity;
import com.leadboard.team.TeamRepository;
import com.leadboard.tenant.TenantContext;
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

    public Flux<ChatSseEvent> processMessage(String sessionId, String userMessage, String currentPage) {
        if (!chatProperties.isEnabled()) {
            return Flux.just(ChatSseEvent.error("Chat is not enabled", sessionId));
        }

        // Capture request-thread context BEFORE Flux — ThreadLocals are lost in reactive threads
        Long tenantId = TenantContext.getCurrentTenantId();
        String tenantSchema = TenantContext.hasTenant() ? TenantContext.getCurrentSchema() : null;
        LlmMessage systemPrompt = buildSystemPrompt(currentPage);

        return Flux.create(sink -> {
            try {
                // Restore tenant context on the Flux execution thread
                if (tenantId != null && tenantSchema != null) {
                    TenantContext.setTenant(tenantId, tenantSchema);
                }
                List<LlmMessage> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());

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

    private LlmMessage buildSystemPrompt(String currentPage) {
        LeadBoardAuthentication auth = authorizationService.getCurrentAuth();
        String role = auth != null ? auth.getRole().name() : "VIEWER";
        String userName = auth != null ? auth.getName() : "Unknown";
        boolean isAdmin = authorizationService.isAdmin() || authorizationService.isProjectManager();

        List<TeamEntity> allTeams = teamRepository.findByActiveTrue();
        String allTeamsInfo = allTeams.stream()
                .map(t -> t.getName() + " (id=" + t.getId() + ")")
                .collect(Collectors.joining(", "));

        Set<Long> userTeamIds = authorizationService.getUserTeamIds();
        String userTeamsInfo;
        if (isAdmin) {
            userTeamsInfo = "все (ADMIN имеет доступ ко всем командам)";
        } else if (!userTeamIds.isEmpty()) {
            userTeamsInfo = allTeams.stream()
                    .filter(t -> userTeamIds.contains(t.getId()))
                    .map(t -> t.getName() + " (id=" + t.getId() + ")")
                    .collect(Collectors.joining(", "));
        } else {
            userTeamsInfo = "не назначены";
        }

        String pageContext = "";
        if (currentPage != null && !currentPage.isBlank()) {
            String pageToolHint = switch (currentPage.toLowerCase()) {
                case String p when p.contains("bug-metrics") || p.contains("bug") -> "\nПредпочитай tool bug_metrics для этой страницы.";
                case String p when p.contains("projects") -> "\nПредпочитай tools project_list и rice_ranking для этой страницы.";
                case String p when p.contains("team-members") || p.contains("members") -> "\nПредпочитай tools team_members и member_absences для этой страницы.";
                case String p when p.contains("data-quality") -> "\nПредпочитай tool data_quality_summary для этой страницы.";
                case String p when p.contains("metrics") -> "\nПредпочитай tool team_metrics для этой страницы.";
                case String p when p.contains("board") -> "\nПредпочитай tool epic_progress для этой страницы — он возвращает те же данные что видит пользователь.";
                default -> "";
            };
            pageContext = """

                Текущая страница пользователя: %s
                Учитывай контекст страницы при ответе. Если вопрос связан с тем, что пользователь видит на экране — отвечай в контексте этой страницы.%s
                """.formatted(currentPage, pageToolHint);
        }

        String prompt = """
                Ты — AI-ассистент системы LeadBoard. Отвечай на русском языке, кратко и по делу.

                Роль пользователя: %s
                Имя пользователя: %s
                Команды пользователя: %s
                Все команды в системе: %s
                %s
                ПРАВИЛА:
                1. ВСЕГДА используй инструменты (tools) для получения данных. НИКОГДА не отвечай "у меня нет данных" — сначала вызови подходящий tool.
                2. Для вопросов "какие задачи/стори/эпики" — используй tool task_search.
                3. Для вопросов "сколько задач" — используй tool task_count.
                4. Для вопросов "какие команды" — используй tool team_list.
                5. Для метрик команды — используй tool team_metrics.
                6. Для обзора доски — используй tool board_summary.
                7. Если пользователь TEAM_LEAD или MEMBER — показывай данные только его команд.
                8. Форматируй ответы с markdown: жирный текст, списки.
                9. Для bug metrics, SLA, открытых багов — используй tool bug_metrics.
                10. Для списка проектов, прогресса проектов — используй tool project_list.
                11. Для RICE-ранжирования, приоритизации — используй tool rice_ranking.
                12. Для отпусков, отсутствий, больничных — используй tool member_absences.
                13. Для SLA настроек багов — используй tool bug_sla_settings.
                14. Для деталей конкретной задачи по ключу (PROJ-123) — используй tool task_details.
                15. Для списка участников команды, состава команды — используй tool team_members.
                16. Для прогресса эпика, оценок, role-based progress, сколько сделано — используй tool epic_progress. Если спрашивают про конкретный эпик — передай query с названием или ключом.

                БАЗА ЗНАНИЙ:
                %s
                """.formatted(role, userName, userTeamsInfo, allTeamsInfo, pageContext, knowledgeBase);

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
