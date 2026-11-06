package io.averkhoglyad.shortcut.notification

import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Instant

/**
 * Signs a token with the test key pair from [TestSecurityConfiguration]. The security chain
 * validates it through the reactive decoder, so tests exercise the real token path instead of
 * injecting an authentication directly.
 */
internal fun signedToken(encoder: JwtEncoder, subject: String): String {
    val issuedAt = Instant.now()
    val claims = JwtClaimsSet.builder()
        .subject(subject)
        .issuedAt(issuedAt)
        .expiresAt(issuedAt.plusSeconds(300))
        .build()
    return encoder.encode(JwtEncoderParameters.from(claims)).tokenValue
}
