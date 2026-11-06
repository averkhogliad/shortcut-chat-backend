package io.averkhoglyad.shortcut.users.security

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken

/**
 * Token request for the `urn:shortcut:oauth:grant-type:email-otp` grant. The token is
 * unauthenticated: it only carries the client principal plus the email and code to
 * verify in [EmailOtpGrantAuthenticationProvider].
 */
class EmailOtpGrantAuthenticationToken(
    val email: String,
    val code: String,
    clientPrincipal: Authentication,
    additionalParameters: Map<String, Any> = emptyMap(),
) : OAuth2AuthorizationGrantAuthenticationToken(
    EMAIL_OTP_GRANT_TYPE,
    clientPrincipal,
    additionalParameters,
) {

    init {
        require(email.isNotBlank()) { "email cannot be blank" }
        require(code.isNotBlank()) { "code cannot be blank" }
    }

}
