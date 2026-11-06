package io.averkhoglyad.shortcut.chat.config

import io.averkhoglyad.shortcut.common.security.SecurityContextBasedSecurityService
import io.averkhoglyad.shortcut.common.security.SecurityService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * The `chats` service is a JWT resource server: every request except the actuator
 * endpoints must carry a Bearer token. Identity is taken from the token `sub` claim by the
 * endpoints themselves; no role checks exist in this cycle.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().authenticated()
            }
            .csrf { csrf -> csrf.disable() }
            .formLogin { formLogin -> formLogin.disable() }
            .httpBasic { httpBasic -> httpBasic.disable() }
            .logout { logout -> logout.disable() }
            .oauth2ResourceServer { resourceServer -> resourceServer.jwt { } }
        return http.build()
    }

    @Bean
    fun securityService(): SecurityService = SecurityContextBasedSecurityService()
}
