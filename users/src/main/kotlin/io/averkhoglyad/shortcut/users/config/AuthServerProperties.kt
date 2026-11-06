package io.averkhoglyad.shortcut.users.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

@ConfigurationProperties(prefix = "spring.security.oauth2.authorizationserver")
data class AuthServerProperties(
    val issuer: String,
    val jwk: Jwk,
    val client: Client,
) {

    data class Jwk(
        val file: Path,
    )

    data class Client(
        val id: String,
        val secret: String,
        val redirectUri: String,
    )
}
