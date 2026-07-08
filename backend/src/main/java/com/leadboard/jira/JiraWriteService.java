package com.leadboard.jira;

import com.leadboard.auth.OAuthService;
import com.leadboard.config.JiraConfigResolver;
import com.leadboard.config.service.WorkflowConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Фасад write-операций в Jira от имени текущего пользователя (F80, фаза write).
 *
 * <p>Все вызовы используют OAuth-токен текущего пользователя (его Jira-аккаунт),
 * поэтому worklog/переходы/комментарии атрибутируются реальному человеку.
 * Если у пользователя нет валидного OAuth-токена — операция отклоняется.</p>
 *
 * <p>Jira — источник истины: пишем в Jira, локальная БД догонит следующим sync.</p>
 */
@Service
public class JiraWriteService {

    private static final Logger log = LoggerFactory.getLogger(JiraWriteService.class);

    private final JiraClient jiraClient;
    private final OAuthService oauthService;
    private final WorkflowConfigService workflowConfigService;
    private final JiraConfigResolver configResolver;

    public JiraWriteService(JiraClient jiraClient, OAuthService oauthService,
                            WorkflowConfigService workflowConfigService, JiraConfigResolver configResolver) {
        this.jiraClient = jiraClient;
        this.oauthService = oauthService;
        this.workflowConfigService = workflowConfigService;
        this.configResolver = configResolver;
    }

    /** Учётные данные OAuth текущего пользователя для записи в Jira. */
    private record Creds(String accessToken, String cloudId) {}

    /** Бросается {@link #logWorkAs}, когда у указанного пользователя нет валидного OAuth-токена Jira. */
    public static class NoUserTokenException extends RuntimeException {
        public NoUserTokenException(String m) {
            super(m);
        }
    }

    private Creds requireCreds() {
        String token = oauthService.getValidAccessToken();
        String cloudId = oauthService.getCloudIdForCurrentUser();
        if (token == null || cloudId == null) {
            throw new IllegalStateException(
                    "Нет валидного OAuth-токена Jira у пользователя. Войдите через Atlassian, чтобы выполнять действия.");
        }
        return new Creds(token, cloudId);
    }

    /**
     * Перевести задачу в целевой статус. target может быть именем перехода,
     * именем целевого статуса или намерением (done/закрыть, in progress/в работу).
     *
     * @return имя нового статуса
     */
    public String transition(String issueKey, String target) {
        Creds c = requireCreds();
        List<JiraTransition> transitions = jiraClient.getTransitions(issueKey, c.accessToken(), c.cloudId());
        if (transitions.isEmpty()) {
            throw new IllegalStateException("Для " + issueKey + " нет доступных переходов из текущего статуса.");
        }
        JiraTransition match = findTransition(transitions, target);
        if (match == null) {
            String available = transitions.stream()
                    .map(t -> t.to() != null ? t.to().name() : t.name())
                    .distinct().reduce((a, b) -> a + ", " + b).orElse("");
            throw new IllegalArgumentException(
                    "Не нашёл перехода в '" + target + "' для " + issueKey + ". Доступные статусы: " + available);
        }
        jiraClient.transitionIssue(issueKey, match.id(), c.accessToken(), c.cloudId());
        String newStatus = match.to() != null ? match.to().name() : match.name();
        log.info("Jira transition: {} -> {} (by current user)", issueKey, newStatus);
        return newStatus;
    }

    private JiraTransition findTransition(List<JiraTransition> transitions, String target) {
        String t = target == null ? "" : target.trim().toLowerCase();
        // 1) точное совпадение по имени перехода или целевого статуса
        for (JiraTransition tr : transitions) {
            if (tr.name() != null && tr.name().equalsIgnoreCase(target)) return tr;
            if (tr.to() != null && tr.to().name() != null && tr.to().name().equalsIgnoreCase(target)) return tr;
        }
        // 2) намерение «закрыть / готово / done»
        if (matchesAny(t, "done", "закр", "готов", "close", "заверш")) {
            JiraTransition done = byCategory(transitions, "done");
            if (done != null) return done;
        }
        // 3) намерение «в работу / in progress»
        if (matchesAny(t, "progress", "работ", "start", "взять", "развит", "develop")) {
            JiraTransition inProg = byCategory(transitions, "indeterminate");
            if (inProg != null) return inProg;
        }
        // 4) частичное совпадение по подстроке
        for (JiraTransition tr : transitions) {
            if (tr.to() != null && tr.to().name() != null && tr.to().name().toLowerCase().contains(t)) return tr;
        }
        return null;
    }

    private JiraTransition byCategory(List<JiraTransition> transitions, String categoryKey) {
        for (JiraTransition tr : transitions) {
            if (tr.to() != null && tr.to().statusCategory() != null
                    && categoryKey.equalsIgnoreCase(tr.to().statusCategory().key())) {
                return tr;
            }
        }
        return null;
    }

    private boolean matchesAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) return true;
        }
        return false;
    }

    /** Залогировать время на задачу от имени текущего пользователя. */
    public void logWork(String issueKey, int timeSpentSeconds, LocalDate date) {
        Creds c = requireCreds();
        LocalDate when = date != null ? date : LocalDate.now();
        jiraClient.addWorklog(issueKey, timeSpentSeconds, when, c.accessToken(), c.cloudId());
        log.info("Jira worklog: {} +{}s on {}", issueKey, timeSpentSeconds, when);
    }

    /**
     * Залогировать время на задачу от имени КОНКРЕТНОГО пользователя (F90 — My Work),
     * используя его личный OAuth-токен, а не {@link #requireCreds()} (последний токен
     * тенанта, которым пользуется AI-чат). Так worklog атрибутируется реальному автору.
     *
     * @param atlassianAccountId Atlassian account id пользователя, от чьего имени пишем
     * @return id созданного worklog'а
     * @throws NoUserTokenException если у пользователя нет валидного OAuth-токена или не
     *         удалось определить Jira cloudId для записи
     */
    public String logWorkAs(String atlassianAccountId, String issueKey, int timeSpentSeconds,
                            LocalDate date, String comment) {
        OAuthService.TokenInfo token = oauthService.getValidAccessTokenForUser(atlassianAccountId);
        if (token == null || token.accessToken() == null) {
            throw new NoUserTokenException(
                    "Нет валидного OAuth-токена Jira у пользователя " + atlassianAccountId + ".");
        }
        String cloudId = resolveWriteCloudId(token);
        if (cloudId == null || cloudId.isBlank()) {
            throw new NoUserTokenException(
                    "Не удалось определить Jira cloudId для пользователя " + atlassianAccountId + ".");
        }
        LocalDate when = date != null ? date : LocalDate.now();
        String worklogId = jiraClient.addWorklogReturningId(
                issueKey, timeSpentSeconds, when, comment, token.accessToken(), cloudId);
        log.info("Jira worklog (as {}): {} +{}s on {}", atlassianAccountId, issueKey, timeSpentSeconds, when);
        return worklogId;
    }

    /**
     * Resolves the Jira cloudId to write to for {@link #logWorkAs}: prefers the ACTIVE
     * TENANT's configured site ({@link JiraConfigResolver#getJiraCloudId()}) over the
     * cloudId stored on the user's OAuth token (the site they first logged into) — a member
     * of two tenants on different Jira sites must write to the CURRENT tenant's site, not
     * whichever one their token happens to remember. The 3LO access token itself is
     * site-agnostic; only the {@code /ex/jira/{cloudId}} path segment picks the site. Falls
     * back to the token's cloudId when there's no tenant config (single-tenant/.env mode).
     * Same precedence as {@link com.leadboard.tenant.TenantAccessReconciler}.
     */
    private String resolveWriteCloudId(OAuthService.TokenInfo token) {
        String tenantCloudId = configResolver.getJiraCloudId();
        if (tenantCloudId != null && !tenantCloudId.isBlank()) {
            return tenantCloudId;
        }
        return token.cloudId();
    }

    /** Добавить комментарий к задаче. */
    public void comment(String issueKey, String text) {
        Creds c = requireCreds();
        jiraClient.addComment(issueKey, text, c.accessToken(), c.cloudId());
        log.info("Jira comment added: {}", issueKey);
    }

    /** Назначить задачу пользователю (accountId=null — снять назначение). */
    public void assign(String issueKey, String accountId) {
        Creds c = requireCreds();
        jiraClient.assignIssue(issueKey, accountId, c.accessToken(), c.cloudId());
        log.info("Jira assign: {} -> {}", issueKey, accountId);
    }

    /**
     * Создать Историю или Баг. Эпики/Проекты создавать запрещено (F80 policy).
     *
     * @param kind "story" или "bug"
     * @param summary заголовок
     * @param parentEpicKey эпик-родитель (опц.; задаёт project key)
     * @return ключ созданной задачи
     */
    public String createIssue(String kind, String summary, String parentEpicKey) {
        requireCreds(); // JiraClient.createIssue резолвит токен сам, но проверим заранее
        String typeName = resolveCreatableType(kind);
        String projectKey = resolveProjectKey(parentEpicKey);
        String key = jiraClient.createIssue(projectKey, typeName, summary, parentEpicKey);
        log.info("Jira issue created: {} ({}) in {}", key, typeName, projectKey);
        return key;
    }

    /** Создать подзадачу под историей/багом для роли. */
    public String createSubtask(String parentKey, String summary, String roleCode) {
        requireCreds();
        String typeName = workflowConfigService.getSubtaskTypeName(roleCode);
        String projectKey = resolveProjectKey(parentKey);
        String key = jiraClient.createSubtask(parentKey, summary, projectKey, typeName);
        log.info("Jira subtask created: {} under {}", key, parentKey);
        return key;
    }

    /** Разрешены только Story/Bug (политика F80 — без Epic/Project). */
    private String resolveCreatableType(String kind) {
        String k = kind == null ? "story" : kind.trim().toLowerCase();
        if (k.startsWith("bug") || k.contains("баг")) {
            List<String> bugTypes = workflowConfigService.getBugTypeNames();
            if (bugTypes.isEmpty()) {
                throw new IllegalStateException("Тип 'Баг' не сконфигурирован в workflow.");
            }
            return bugTypes.get(0);
        }
        if (k.startsWith("story") || k.contains("истор") || k.contains("задач")) {
            return workflowConfigService.getStoryTypeName();
        }
        throw new IllegalArgumentException(
                "Создавать можно только Истории и Баги. Эпики и Проекты создавать нельзя.");
    }

    private String resolveProjectKey(String anyKey) {
        if (anyKey != null && anyKey.contains("-")) {
            return anyKey.substring(0, anyKey.indexOf('-'));
        }
        String pk = configResolver.getProjectKey();
        if (pk == null || pk.isBlank()) {
            List<String> active = configResolver.getActiveProjectKeys();
            if (!active.isEmpty()) return active.get(0);
        }
        if (pk == null || pk.isBlank()) {
            throw new IllegalStateException("Не удалось определить project key для создания задачи.");
        }
        return pk;
    }
}
