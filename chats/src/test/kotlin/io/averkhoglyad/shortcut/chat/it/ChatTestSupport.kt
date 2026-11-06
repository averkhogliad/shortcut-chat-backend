package io.averkhoglyad.shortcut.chat.it

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import java.util.UUID

/**
 * Mock JWT authenticating the request as the user identified by its `sub` claim.
 */
internal fun jwtFor(userId: UUID) = jwt().jwt { it.subject(userId.toString()) }
