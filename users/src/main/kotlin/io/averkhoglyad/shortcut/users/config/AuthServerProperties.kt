package io.averkhoglyad.shortcut.users.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path

@ConfigurationProperties(prefix = "spring.security.oauth2.authorizationserver")
data class AuthServerProperties(
    val issuer: String,
    val jwk: Jwk,
    val client: Client,
    val serviceClient: ServiceClient,
) {

    data class Jwk(
        val file: Path,
    )

    data class Client(
        val id: String,
        val secret: String,
        val redirectUri: String,
    )

    /**
     * Confidential client used by backend services for the `client_credentials` grant.
     * It has no user, redirect URI, or scopes: the issued token only proves the service identity.
     */
    data class ServiceClient(
        val id: String,
        val secret: String,
    )
}
