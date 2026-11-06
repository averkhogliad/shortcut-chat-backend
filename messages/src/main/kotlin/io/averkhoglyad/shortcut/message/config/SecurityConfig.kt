package io.averkhoglyad.shortcut.message.config

import io.averkhoglyad.shortcut.common.security.SecurityContextBasedSecurityService
import io.averkhoglyad.shortcut.common.security.SecurityService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

/**
 * The service is a JWT resource server: every request except the actuator probes needs a
 * valid Bearer token. Role checks are deferred, so the chain declares no role rules.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher("/actuator/**"))
                    .permitAll()
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
