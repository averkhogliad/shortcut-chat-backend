package io.averkhoglyad.shortcut.users.config

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import io.averkhoglyad.shortcut.users.security.EMAIL_OTP_GRANT_TYPE
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import java.time.Duration
import java.util.*

private val REFRESH_TOKEN_TIME_TO_LIVE: Duration = Duration.ofHours(9)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthServerProperties::class)
class AuthServerConfig {

    @Bean
    fun authorizationServerSettings(properties: AuthServerProperties): AuthorizationServerSettings =
        AuthorizationServerSettings.builder()
            .issuer(properties.issuer)
            .build()

    @Bean
    fun jwkSource(properties: AuthServerProperties): JWKSource<SecurityContext> =
        ImmutableJWKSet(JWKSet(loadOrCreateRsaKey(properties.jwk.file)))

    @Bean
    fun jwtDecoder(jwkSource: JWKSource<SecurityContext>): JwtDecoder =
        OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)

    @Bean
    fun registeredClientRepository(
        properties: AuthServerProperties,
        passwordEncoder: PasswordEncoder,
    ): RegisteredClientRepository {
        val client = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(properties.client.id)
            .clientSecret(requireNotNull(passwordEncoder.encode(properties.client.secret)))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorizationGrantType(EMAIL_OTP_GRANT_TYPE)
            .redirectUri(properties.client.redirectUri)
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            .scope(OidcScopes.EMAIL)
            .tokenSettings(
                TokenSettings.builder()
                    .refreshTokenTimeToLive(REFRESH_TOKEN_TIME_TO_LIVE)
                    .build()
            )
            .build()
        val serviceClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(properties.serviceClient.id)
            .clientSecret(requireNotNull(passwordEncoder.encode(properties.serviceClient.secret)))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .build()
        return InMemoryRegisteredClientRepository(client, serviceClient)
    }

    /**
     * Adds profile claims to ID tokens only. The principal is expected to expose `email`
     * and `name` attributes; access tokens keep their standard claim set.
     */
    @Bean
    fun idTokenCustomizer(): OAuth2TokenCustomizer<JwtEncodingContext> =
        OAuth2TokenCustomizer { context ->
            if (context.tokenType.value == OidcParameterNames.ID_TOKEN) {
                val principal = context.getPrincipal<Authentication>()
                principal.attribute("email")?.let { context.claims.claim("email", it) }
                principal.attribute("name")?.let { context.claims.claim("name", it) }
            }
        }
}

private fun Authentication?.attribute(name: String): Any? =
    (this?.principal as? OAuth2User)?.attributes?.get(name)
