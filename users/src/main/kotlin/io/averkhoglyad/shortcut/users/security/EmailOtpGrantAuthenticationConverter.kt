package io.averkhoglyad.shortcut.users.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.web.authentication.AuthenticationConverter
import org.springframework.util.StringUtils

private const val EMAIL_PARAMETER = "email"

/**
 * Converts a token request with `grant_type=urn:shortcut:oauth:grant-type:email-otp`
 * into an unauthenticated [EmailOtpGrantAuthenticationToken]. Any other grant type
 * returns `null` so the default converters keep handling it.
 */
class EmailOtpGrantAuthenticationConverter : AuthenticationConverter {

    override fun convert(request: HttpServletRequest): Authentication? {
        if (request.getParameter(OAuth2ParameterNames.GRANT_TYPE) != EMAIL_OTP_GRANT_TYPE.value) {
            return null
        }

        val clientPrincipal = SecurityContextHolder.getContext().authentication
            ?: throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT)

        return EmailOtpGrantAuthenticationToken(
            email = singleParameter(request, EMAIL_PARAMETER),
            code = singleParameter(request, OAuth2ParameterNames.CODE),
            clientPrincipal = clientPrincipal,
        )
    }

    private fun singleParameter(request: HttpServletRequest, name: String): String {
        val values = request.getParameterValues(name)
        if (values == null || values.size != 1 || !StringUtils.hasText(values[0])) {
            throw OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST)
        }
        return values[0]
    }

}
