package io.averkhoglyad.shortcut.users.config

import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import io.averkhoglyad.shortcut.users.security.EMAIL_OTP_GRANT_TYPE
import io.averkhoglyad.shortcut.users.security.EmailOtpGrantAuthenticationConverter
import io.averkhoglyad.shortcut.users.security.EmailOtpGrantAuthenticationProvider
import io.averkhoglyad.shortcut.users.core.service.OtpService
import io.averkhoglyad.shortcut.users.core.service.UserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.OAuth2Token
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.AndRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher

/**
 * The authorization server endpoints. Token introspection is deliberately absent: tokens
 * are validated against the published JWKs, not by asking this service.
 */
private val AUTHORIZATION_SERVER_PATHS: RequestMatcher = OrRequestMatcher(
    PathPatternRequestMatcher.withDefaults().matcher("/oauth2/**"),
    PathPatternRequestMatcher.withDefaults().matcher("/.well-known/**"),
    PathPatternRequestMatcher.withDefaults().matcher("/userinfo"),
)

private val TOKEN_INTROSPECTION_ENDPOINT: RequestMatcher =
    PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/oauth2/introspect")

private val INTROSPECTION_METADATA_CLAIMS = listOf(
    "introspection_endpoint",
    "introspection_endpoint_auth_methods_supported",
)

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfig {

    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(
        http: HttpSecurity,
        otpService: OtpService,
        userService: UserService,
        authorizationService: OAuth2AuthorizationService,
        tokenGenerator: OAuth2TokenGenerator<out OAuth2Token>,
    ): SecurityFilterChain {
        val emailOtpConverter = EmailOtpGrantAuthenticationConverter()
        val emailOtpProvider = EmailOtpGrantAuthenticationProvider(
            otpService = otpService,
            userService = userService,
            authorizationService = authorizationService,
            tokenGenerator = tokenGenerator,
        )
        http
            .securityMatcher(authorizationServerEndpoints())
            .oauth2AuthorizationServer { server ->
                server
                    .tokenEndpoint { endpoint ->
                        endpoint
                            .accessTokenRequestConverter(emailOtpConverter)
                            .authenticationProvider(emailOtpProvider)
                    }
                    .authorizationServerMetadataEndpoint { endpoint ->
                        endpoint.authorizationServerMetadataCustomizer { metadata ->
                            metadata.claims { claims -> claims.keys.removeAll(INTROSPECTION_METADATA_CLAIMS) }
                        }
                    }
                    .oidc { oidc ->
                        oidc.providerConfigurationEndpoint { endpoint ->
                            endpoint.providerConfigurationCustomizer { metadata ->
                                metadata.grantType(EMAIL_OTP_GRANT_TYPE.value)
                                metadata.claims { claims ->
                                    claims.keys.removeAll(INTROSPECTION_METADATA_CLAIMS)
                                    claims.remove("end_session_endpoint")
                                }
                            }
                        }
                    }
            }
            .authorizeHttpRequests { authorize -> authorize.anyRequest().permitAll() }
            .csrf { csrf -> csrf.disable() }
            .formLogin { formLogin -> formLogin.disable() }
            .httpBasic { httpBasic -> httpBasic.disable() }
            .logout { logout -> logout.disable() }
            .oauth2ResourceServer { resourceServer -> resourceServer.jwt { } }
        return http.build()
    }

    @Bean
    @Order(2)
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authorize -> authorize.anyRequest().permitAll() }
            .csrf { csrf -> csrf.disable() }
            .formLogin { formLogin -> formLogin.disable() }
            .httpBasic { httpBasic -> httpBasic.disable() }
            .logout { logout -> logout.disable() }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authorizationService(): OAuth2AuthorizationService = InMemoryOAuth2AuthorizationService()

    @Bean
    fun tokenGenerator(
        jwkSource: JWKSource<SecurityContext>,
        idTokenCustomizer: OAuth2TokenCustomizer<JwtEncodingContext>,
    ): OAuth2TokenGenerator<out OAuth2Token> {
        val jwtGenerator = JwtGenerator(NimbusJwtEncoder(jwkSource))
            .apply { setJwtCustomizer(idTokenCustomizer) }
        return DelegatingOAuth2TokenGenerator(
            jwtGenerator,
            OAuth2AccessTokenGenerator(),
            OAuth2RefreshTokenGenerator(),
        )
    }
}

/**
 * Chain 1 matches the authorization server paths except the token introspection endpoint:
 * the library registers `/oauth2/introspect` unconditionally, so the request is left
 * outside the chain and no introspection endpoint is served.
 */
private fun authorizationServerEndpoints(): RequestMatcher =
    AndRequestMatcher(AUTHORIZATION_SERVER_PATHS, NegatedRequestMatcher(TOKEN_INTROSPECTION_ENDPOINT))
