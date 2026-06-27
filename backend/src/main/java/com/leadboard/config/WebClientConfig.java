package com.leadboard.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

/**
 * Global WebClient configuration.
 *
 * <p>Reactor-netty's default DNS resolver issues direct UDP DNS queries, which fail
 * on some networks ("UnknownHostException: Failed to resolve 'api.atlassian.com' /
 * 'auth.atlassian.com'"), while the JDK/OS resolver works. This customizer forces the
 * JDK/OS resolver on the Spring auto-configured {@link org.springframework.web.reactive.function.client.WebClient.Builder}.
 *
 * <p>Spring applies every {@link WebClientCustomizer} bean to the auto-configured
 * {@code WebClient.Builder}, so any service that <b>injects</b> {@code WebClient.Builder}
 * and calls {@code .build()} automatically inherits the OS resolver. Services that build
 * their own {@link HttpClient} (e.g. for custom timeouts) must set the resolver explicitly
 * on that {@code HttpClient}, since their {@code .clientConnector(...)} overrides this one.
 */
@Configuration
public class WebClientConfig {

    @Bean
    WebClientCustomizer osResolverCustomizer() {
        HttpClient httpClient = HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE);
        return builder -> builder.clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
