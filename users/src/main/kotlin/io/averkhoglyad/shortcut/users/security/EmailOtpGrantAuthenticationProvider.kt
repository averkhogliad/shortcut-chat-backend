package io.averkhoglyad.shortcut.users.security

import io.averkhoglyad.shortcut.common.data.EntityResult
import io.averkhoglyad.shortcut.users.core.model.User
import io.averkhoglyad.shortcut.users.core.service.OtpService
import io.averkhoglyad.shortcut.users.core.service.OtpVerificationResult
import io.averkhoglyad.shortcut.users.core.service.UserService
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClaimAccessor
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.core.OAuth2Token
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import java.security.Principal

private const val SUBJECT_CLAIM = "sub"
private const val EMAIL_CLAIM = "email"
private const val NAME_CLAIM = "name"
private const val EMAIL_SEPARATOR = "@"
private val ID_TOKEN_TYPE = OAuth2TokenType(OidcParameterNames.ID_TOKEN)

/**
 * Authenticates the email-OTP grant: consumes the OTP, finds or auto-registers the user
 * and only then issues access, ID and refresh tokens. Registration goes through
 * [UserService.create], so the `UserCreated` outbox record commits in the same
 * transaction as the user row; token generation starts after that call returns.
 */
class EmailOtpGrantAuthenticationProvider(
    private val otpService: OtpService,
    private val userService: UserService,
    private val authorizationService: OAuth2AuthorizationService,
    private val tokenGenerator: OAuth2TokenGenerator<out OAuth2Token>,
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {
        val grant = authentication as EmailOtpGrantAuthenticationToken
        val clientPrincipal = grant.authenticatedClient()
        val registeredClient = clientPrincipal.registeredClient
            ?: throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT)

        if (!registeredClient.authorizationGrantTypes.contains(EMAIL_OTP_GRANT_TYPE)) {
            throw OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT)
        }

        if (otpService.verifyCode(grant.email, grant.code) != OtpVerificationResult.SUCCESS) {
            throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT)
        }

        val user = findOrRegisterUser(grant.email)
        val userPrincipal = user.toPrincipal()
        val authorizedScopes = registeredClient.scopes

        val tokenContextBuilder = DefaultOAuth2TokenContext.builder()
            .registeredClient(registeredClient)
            .principal(userPrincipal)
            .authorizationServerContext(AuthorizationServerContextHolder.getContext())
            .authorizedScopes(authorizedScopes)
            .authorizationGrantType(EMAIL_OTP_GRANT_TYPE)
            .authorizationGrant(grant)

        val generatedAccessToken = tokenGenerator.generate(
            tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build()
        ) ?: throw serverError("The token generator failed to generate the access token.")
        val accessToken = OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            generatedAccessToken.tokenValue,
            generatedAccessToken.issuedAt,
            generatedAccessToken.expiresAt,
            authorizedScopes,
        )

        val authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
            .principalName(userPrincipal.name)
            .authorizationGrantType(EMAIL_OTP_GRANT_TYPE)
            .authorizedScopes(authorizedScopes)
            .attribute(Principal::class.java.name, userPrincipal)
        authorizationBuilder.token(accessToken) { metadata ->
            metadata.putClaims(generatedAccessToken)
        }

        val refreshToken = generateRefreshToken(registeredClient, tokenContextBuilder)
        refreshToken?.let { authorizationBuilder.refreshToken(it) }

        val idToken = generateIdToken(authorizedScopes, tokenContextBuilder, authorizationBuilder)
        idToken?.let { token ->
            authorizationBuilder.token(token) { metadata ->
                metadata[OAuth2Authorization.Token.CLAIMS_METADATA_NAME] = token.claims
            }
        }

        authorizationService.save(authorizationBuilder.build())

        val additionalParameters: Map<String, Any> = idToken
            ?.let { mapOf(OidcParameterNames.ID_TOKEN to it.tokenValue) }
            ?: emptyMap()

        return OAuth2AccessTokenAuthenticationToken(
            registeredClient,
            clientPrincipal,
            accessToken,
            refreshToken,
            additionalParameters,
        )
    }

    override fun supports(authentication: Class<*>): Boolean =
        EmailOtpGrantAuthenticationToken::class.java.isAssignableFrom(authentication)

    private fun generateRefreshToken(
        registeredClient: RegisteredClient,
        tokenContextBuilder: DefaultOAuth2TokenContext.Builder,
    ): OAuth2RefreshToken? {
        if (!registeredClient.authorizationGrantTypes.contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            return null
        }
        val generated = tokenGenerator.generate(
            tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build()
        ) ?: return null
        return generated as? OAuth2RefreshToken
            ?: throw serverError("The token generator failed to generate a valid refresh token.")
    }

    private fun generateIdToken(
        authorizedScopes: Set<String>,
        tokenContextBuilder: DefaultOAuth2TokenContext.Builder,
        authorizationBuilder: OAuth2Authorization.Builder,
    ): OidcIdToken? {
        if (!authorizedScopes.contains(OidcScopes.OPENID)) {
            return null
        }
        val generated = tokenGenerator.generate(
            tokenContextBuilder
                .tokenType(ID_TOKEN_TYPE)
                .authorization(authorizationBuilder.build())
                .build()
        ) ?: throw serverError("The token generator failed to generate the ID token.")
        return (generated as? Jwt)
            ?.let { OidcIdToken(it.tokenValue, it.issuedAt, it.expiresAt, it.claims) }
            ?: throw serverError("The token generator failed to generate a valid ID token.")
    }

    private fun findOrRegisterUser(email: String): User =
        when (val found = userService.findByEmail(email)) {
            is EntityResult.Success -> found.entity
            EntityResult.NotFound -> registerUser(email)
            else -> throw serverError("The user lookup failed.")
        }

    /**
     * A parallel first login can win the unique-email race between the lookup and the
     * insert. The insert is then rolled back and the winner's user is reused, so exactly
     * one row and one `UserCreated` record exist.
     */
    private fun registerUser(email: String): User {
        val candidate = User(
            id = null,
            name = email.substringBefore(EMAIL_SEPARATOR),
            email = email,
        )
        return try {
            when (val created = userService.create(candidate)) {
                is EntityResult.Success -> created.entity
                else -> throw serverError("The user registration failed.")
            }
        } catch (ex: DuplicateKeyException) {
            when (val existing = userService.findByEmail(email)) {
                is EntityResult.Success -> existing.entity
                else -> throw ex
            }
        }
    }

    private fun User.toPrincipal(): Authentication {
        val id = requireNotNull(id) { "User id cannot be null" }
        val oauth2User = DefaultOAuth2User(
            emptyList(),
            mapOf(
                SUBJECT_CLAIM to id.toString(),
                EMAIL_CLAIM to email,
                NAME_CLAIM to name,
            ),
            SUBJECT_CLAIM,
        )
        return UsernamePasswordAuthenticationToken(oauth2User, null, oauth2User.authorities)
    }

    private fun EmailOtpGrantAuthenticationToken.authenticatedClient(): OAuth2ClientAuthenticationToken {
        val clientPrincipal = principal as? OAuth2ClientAuthenticationToken
        if (clientPrincipal == null || !clientPrincipal.isAuthenticated) {
            throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT)
        }
        return clientPrincipal
    }

    private fun serverError(description: String): OAuth2AuthenticationException =
        OAuth2AuthenticationException(
            OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR, description, null)
        )

}

private fun MutableMap<String, Any>.putClaims(token: OAuth2Token) {
    val claims = (token as? ClaimAccessor)?.claims ?: return
    this[OAuth2Authorization.Token.CLAIMS_METADATA_NAME] = claims
}
