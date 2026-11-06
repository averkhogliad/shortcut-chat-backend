package io.averkhoglyad.shortcut.common.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.util.*

/**
 * Identity of the authenticated caller. The contract is non-null: an endpoint that reaches it
 * is already behind an authentication requirement, so a missing principal is a wiring defect,
 * not a request-level outcome.
 */
interface SecurityService {

    fun currentUserId(): UUID

}

class SecurityContextBasedSecurityService : SecurityService {

    override fun currentUserId(): UUID {
        val principal = SecurityContextHolder.getContext()
            .authentication
            ?.principal as? Jwt
            ?: throw IllegalStateException("No JWT principal in the security context")

        return UUID.fromString(principal.subject)
    }
}
