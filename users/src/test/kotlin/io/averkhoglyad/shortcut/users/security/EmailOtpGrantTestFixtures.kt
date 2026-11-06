package io.averkhoglyad.shortcut.users.security

import io.averkhoglyad.shortcut.users.test.firstArg
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import java.time.Duration

internal const val TEST_CLIENT_ID = "test-client"
internal const val TEST_CLIENT_SECRET = "test-client-secret"

internal fun testRegisteredClient(includeEmailOtpGrant: Boolean = true): RegisteredClient {
    val builder = RegisteredClient.withId("test-registration")
        .clientId(TEST_CLIENT_ID)
        .clientSecret(TEST_CLIENT_SECRET)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
        .redirectUri("http://localhost/callback")
        .scope(OidcScopes.OPENID)
        .scope(OidcScopes.PROFILE)
        .tokenSettings(TokenSettings.builder().refreshTokenTimeToLive(Duration.ofHours(9)).build())
    if (includeEmailOtpGrant) {
        builder.authorizationGrantType(EMAIL_OTP_GRANT_TYPE)
    }
    return builder.build()
}

internal fun authenticatedClient(
    client: RegisteredClient = testRegisteredClient(),
): OAuth2ClientAuthenticationToken =
    OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, TEST_CLIENT_SECRET)

internal fun tokenRequest(parameters: Map<String, Array<String>>): HttpServletRequest =
    mockk<HttpServletRequest> {
        every { getParameter(any()) } answers { parameters[firstArg<String>()]?.firstOrNull() }
        every { getParameterValues(any()) } answers { parameters[firstArg<String>()] }
    }
