package io.averkhoglyad.shortcut.notification.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * The `notifications` service is a reactive JWT resource server: every exchange except the
 * actuator endpoints must carry a Bearer token. The SSE subscription cannot set request
 * headers from the browser `EventSource` API, so its token arrives in the `token` query
 * parameter and is read by [TokenQueryParameterAuthenticationConverter]. Role checks are
 * deferred, so the chain declares no role rules.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .csrf { csrf -> csrf.disable() }
            .formLogin { formLogin -> formLogin.disable() }
            .httpBasic { httpBasic -> httpBasic.disable() }
            .authorizeExchange { authorize ->
                authorize
                    .pathMatchers("/actuator/**").permitAll()
                    .anyExchange().authenticated()
            }
            .oauth2ResourceServer { resourceServer ->
                resourceServer.bearerTokenConverter(TokenQueryParameterAuthenticationConverter())
                resourceServer.jwt { }
            }
        return http.build()
    }
}
