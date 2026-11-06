package io.averkhoglyad.shortcut.users.config

import io.averkhoglyad.shortcut.users.security.EMAIL_OTP_GRANT_TYPE
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.oidc.OidcScopes
import java.nio.file.Path
import java.time.Duration

class AuthServerConfigTest : FreeSpec({

    val config = AuthServerConfig()
    val passwordEncoder = BCryptPasswordEncoder()
    val properties = AuthServerProperties(
        issuer = "http://localhost:8080",
        jwk = AuthServerProperties.Jwk(Path.of("target", "test-jwk.json")),
        client = AuthServerProperties.Client(
            id = "test-client",
            secret = "test-secret",
            redirectUri = "http://localhost/callback",
        ),
    )

    "authorizationServerSettings" - {
        "uses the configured issuer" {
            // when
            val settings = config.authorizationServerSettings(properties)

            // then
            settings.issuer shouldBe "http://localhost:8080"
        }
    }

    "registeredClientRepository" - {
        "registers the configured client with required grants, scopes and refresh token TTL" {
            // when
            val client = config.registeredClientRepository(properties, passwordEncoder)
                .findByClientId("test-client")
                .shouldNotBeNull()

            // then
            client.authorizationGrantTypes shouldContain AuthorizationGrantType.AUTHORIZATION_CODE
            client.authorizationGrantTypes shouldContain AuthorizationGrantType.REFRESH_TOKEN
            client.authorizationGrantTypes shouldContain EMAIL_OTP_GRANT_TYPE
            // T9-2 supersedes the T2-6 scope list {openid, profile}: the OIDC email
            // scope is what makes the `email` claim available at GET /userinfo.
            client.scopes shouldBe setOf(OidcScopes.OPENID, OidcScopes.PROFILE, OidcScopes.EMAIL)
            client.tokenSettings.refreshTokenTimeToLive shouldBe Duration.ofHours(9)
            client.redirectUris shouldBe setOf("http://localhost/callback")
        }

        "stores the client secret encoded" {
            // when
            val client = config.registeredClientRepository(properties, passwordEncoder)
                .findByClientId("test-client")
                .shouldNotBeNull()

            // then
            client.clientSecret shouldNotBe "test-secret"
            passwordEncoder.matches("test-secret", client.clientSecret) shouldBe true
        }
    }
})
