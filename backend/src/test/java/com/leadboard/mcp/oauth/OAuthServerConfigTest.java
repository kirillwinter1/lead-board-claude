package com.leadboard.mcp.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SECURITY_AUDIT.md §14: the MCP OAuth client must not register
 * {@link ClientAuthenticationMethod#NONE} when a client secret is configured — otherwise the
 * secret is effectively optional (a request presenting no credentials would still authenticate).
 */
class OAuthServerConfigTest {

    private RegisteredClient.Builder newBuilder() {
        return RegisteredClient.withId("test-id")
                .clientId("claude-ai")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://claude.ai/api/mcp/auth_callback");
    }

    @Test
    void withSecret_registersOnlySecretBasedMethods_noNone() {
        RegisteredClient.Builder builder = newBuilder();

        boolean hasSecret = OAuthServerConfig.applyClientAuthenticationMethods(builder, "s3cr3t");

        assertTrue(hasSecret);
        RegisteredClient client = builder.build();
        assertEquals(2, client.getClientAuthenticationMethods().size());
        assertTrue(client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC));
        assertTrue(client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.CLIENT_SECRET_POST));
        assertFalse(client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE));
        assertEquals("{noop}s3cr3t", client.getClientSecret());
    }

    @Test
    void withBlankSecret_registersNoneOnly() {
        RegisteredClient.Builder builder = newBuilder();

        boolean hasSecret = OAuthServerConfig.applyClientAuthenticationMethods(builder, "   ");

        assertFalse(hasSecret);
        RegisteredClient client = builder.build();
        assertEquals(1, client.getClientAuthenticationMethods().size());
        assertTrue(client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE));
    }

    @Test
    void withNullSecret_registersNoneOnly() {
        RegisteredClient.Builder builder = newBuilder();

        boolean hasSecret = OAuthServerConfig.applyClientAuthenticationMethods(builder, null);

        assertFalse(hasSecret);
        RegisteredClient client = builder.build();
        assertEquals(1, client.getClientAuthenticationMethods().size());
        assertTrue(client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE));
    }
}
