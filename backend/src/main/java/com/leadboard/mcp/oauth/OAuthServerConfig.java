package com.leadboard.mcp.oauth;

import com.leadboard.auth.LeadBoardAuthentication;
import com.leadboard.auth.LeadBoardAuthenticationFilter;
import com.leadboard.mcp.McpProperties;
import com.leadboard.tenant.TenantFilter;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.http.MediaType;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * OAuth 2.1 Authorization Server для подключения MCP с телефона/claude.ai (F80 Plan 2).
 *
 * <p>Активен только при {@code mcp.oauth-enabled=true}. Аддитивен: НЕ трогает основной
 * {@link com.leadboard.config.SecurityConfig}. Две цепочки: AS-endpoints (@Order 1) и
 * JWT resource server для /mcp (@Order 2). Клиент claude-ai pre-registered (DCR не требуется).</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "mcp", name = "oauth-enabled", havingValue = "true")
public class OAuthServerConfig {

    private static final Logger log = LoggerFactory.getLogger(OAuthServerConfig.class);

    private final McpProperties props;

    public OAuthServerConfig(McpProperties props) {
        this.props = props;
    }

    // ---- Authorization Server endpoints chain (authorize/token/jwks/revoke) ----
    @Bean
    @Order(1)
    public SecurityFilterChain authServerChain(HttpSecurity http,
                                               TenantFilter tenantFilter,
                                               LeadBoardAuthenticationFilter authenticationFilter) throws Exception {
        // Spring AS 1.2.x: applyDefaultSecurity ставит securityMatcher на протокольные endpoints + отключает csrf.
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults());
        http
                // переиспользуем session-аутентификацию: tenant + LeadBoardAuthentication из cookie.
                // ВАЖНО: ставим ДО AuthorizationServerContextFilter, чтобы principal был установлен
                // к моменту обработки /oauth2/authorize.
                .addFilterAfter(tenantFilter, SecurityContextHolderFilter.class)
                .addFilterAfter(authenticationFilter, TenantFilter.class)
                // если не залогинен — отправляем на наш вход (Atlassian), не на дефолтный form login
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/oauth/atlassian/authorize"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    // ---- MCP resource server chain: /mcp protected by JWT ----
    @Bean
    @Order(2)
    public SecurityFilterChain mcpResourceChain(HttpSecurity http,
                                                JwtDecoder jwtDecoder,
                                                McpJwtContextFilter jwtContextFilter) throws Exception {
        http.securityMatcher("/mcp", "/mcp/**")
                // permitAll на уровне AuthorizationFilter: MCP servlet асинхронный (streamable), и
                // authenticated()-проверка конфликтует с async re-dispatch (даёт 401 после Secured).
                // Гейт — McpJwtContextFilter: нет валидного JWT → 401. JWT валидирует BearerTokenAuthenticationFilter.
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setHeader("WWW-Authenticate",
                                    "Bearer resource_metadata=\"" + props.getIssuer()
                                            + "/.well-known/oauth-protected-resource\", scope=\"mcp.read\"");
                        }))
                // после валидации JWT — ставим TenantContext + LeadBoardAuthentication из claims
                .addFilterAfter(jwtContextFilter, org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    // In-memory: избегаем JDBC-сериализации кастомного principal (LeadBoardAuthentication),
    // которая ломает authorize. Persistence токенов и так эфемерна (RSA-ключ генерируется при старте).
    @Bean
    public OAuth2AuthorizationService oauth2AuthorizationService() {
        return new org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService();
    }

    @Bean
    public OAuth2AuthorizationConsentService consentService() {
        return new org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService();
    }

    /** Регистрирует клиента claude-ai при старте (pre-registration вместо анонимной DCR). */
    @Bean
    public ApplicationRunner registerClaudeClient(RegisteredClientRepository repo) {
        return args -> {
            // Перезаписываем существующего клиента (обновляем настройки), используя его же id.
            RegisteredClient existing = repo.findByClientId(props.getClientId());
            String id = existing != null ? existing.getId() : UUID.randomUUID().toString();
            RegisteredClient.Builder b = RegisteredClient.withId(id)
                    .clientId(props.getClientId())
                    .clientName("Claude (MCP)")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri("https://claude.ai/api/mcp/auth_callback")
                    .redirectUri("https://claude.com/api/mcp/auth_callback")
                    .redirectUri("http://localhost:6274/oauth/callback") // MCP Inspector
                    .scope("mcp.read")
                    .scope("mcp.invoke")
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)               // PKCE S256
                            .requireAuthorizationConsent(false)  // already logged in → no consent screen
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofMinutes(60))
                            .reuseRefreshTokens(false)           // rotation (OAuth 2.1 public client)
                            .refreshTokenTimeToLive(Duration.ofDays(30))
                            .build());

            boolean hasSecret = applyClientAuthenticationMethods(b, props.getClientSecret());
            repo.save(b.build());
            log.info("Registered/updated OAuth client '{}' for MCP ({} auth)", props.getClientId(),
                    hasSecret ? "basic+post" : "none/PKCE public-client");
        };
    }

    /**
     * Sets the client-authentication method(s) on the builder based on whether a client
     * secret is configured. Extracted as a static, side-effect-free method for unit testing
     * without booting the OAuth authorization server.
     *
     * <p>SECURITY_AUDIT.md §14: {@link ClientAuthenticationMethod#NONE} must NOT be
     * registered alongside {@code CLIENT_SECRET_BASIC}/{@code CLIENT_SECRET_POST} — doing so
     * makes a configured secret effectively optional (any request presenting no credentials
     * would still authenticate). When a secret IS configured, only secret-based methods are
     * registered. When no secret is configured (public client), {@code NONE} is registered —
     * PKCE (mandatory via {@code requireProofKey(true)}) is the sole client-auth guarantee
     * in that case.</p>
     *
     * @return true if a non-blank secret was applied (basic+post), false if the client was
     *         configured as a public client (NONE)
     */
    static boolean applyClientAuthenticationMethods(RegisteredClient.Builder builder, String clientSecret) {
        if (clientSecret != null && !clientSecret.isBlank()) {
            // claude.ai может слать секрет и в заголовке (basic), и в теле (post) — принимаем оба.
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .clientSecret("{noop}" + clientSecret);
            return true;
        }
        // Public client (без секрета): NONE + обязательный PKCE (requireProofKey(true) выше).
        builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        return false;
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        // MVP: RSA-ключ генерируется при старте. ВНИМАНИЕ для прода: вынести в стабильный keystore,
        // иначе рестарт инвалидирует все выданные токены.
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            RSAKey rsa = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
                    .privateKey((RSAPrivateKey) kp.getPrivate())
                    .keyID(UUID.randomUUID().toString())
                    .build();
            log.warn("MCP OAuth: RSA key generated at startup (tokens invalidated on restart). Use a keystore in prod.");
            return new ImmutableJWKSet<>(new JWKSet(rsa));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key for OAuth", e);
        }
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        // Audience validator через jwt.getAudience() — нормализует aud (String ИЛИ массив) в List<String>.
        // (JwtClaimValidator<List> падал, когда aud сериализован как одиночная строка.)
        OAuth2TokenValidator<org.springframework.security.oauth2.jwt.Jwt> audienceValidator = jwt ->
                jwt.getAudience() != null && jwt.getAudience().contains(props.resourceUri())
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new org.springframework.security.oauth2.core.OAuth2Error(
                                "invalid_token", "Required audience " + props.resourceUri() + " missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.getIssuer()),
                audienceValidator));
        return decoder;
    }

    /** Добавляет tenant_id, user_account_id и audience в access token. */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            if (!"access_token".equals(context.getTokenType().getValue())) {
                return;
            }
            Object principal = context.getPrincipal().getPrincipal();
            String tenantId = null;
            String accountId = null;
            if (context.getPrincipal() instanceof LeadBoardAuthentication lba) {
                tenantId = lba.getTenantId() != null ? String.valueOf(lba.getTenantId()) : null;
                accountId = lba.getAtlassianAccountId();
            }
            final String tid = tenantId;
            final String aid = accountId;
            context.getClaims().claims(c -> {
                if (tid != null) c.put("tenant_id", tid);
                if (aid != null) c.put("user_account_id", aid);
                c.put("aud", List.of(props.resourceUri()));
            });
        };
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(props.getIssuer())
                .build();
    }
}
