package io.averkhoglyad.shortcut.users.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.nio.file.Path

private const val PREFIX = "spring.security.oauth2.authorizationserver"

class AuthServerPropertiesTest : FreeSpec({

    "AuthServerProperties" - {
        "binds issuer, jwk file and client settings" {
            // given
            val jwkFile = Path.of(System.getProperty("java.io.tmpdir"), "shortcut-chat", "jwk.json")

            // when
            val properties = bind(
                "$PREFIX.issuer" to "http://localhost:8080",
                "$PREFIX.jwk.file" to jwkFile.toString(),
                "$PREFIX.client.id" to "test-client",
                "$PREFIX.client.secret" to "test-secret",
                "$PREFIX.client.redirect-uri" to "http://localhost/callback",
                "$PREFIX.service-client.id" to "test-service-client",
                "$PREFIX.service-client.secret" to "test-service-client-secret",
            )

            // then
            properties.issuer shouldBe "http://localhost:8080"
            properties.jwk.file shouldBe jwkFile
            properties.client.id shouldBe "test-client"
            properties.client.secret shouldBe "test-secret"
            properties.client.redirectUri shouldBe "http://localhost/callback"
            properties.serviceClient.id shouldBe "test-service-client"
            properties.serviceClient.secret shouldBe "test-service-client-secret"
        }

        "fails to bind when issuer is missing" {
            // when
            val error = shouldThrow<Exception> {
                bind(
                    "$PREFIX.jwk.file" to "target/jwk.json",
                    "$PREFIX.client.id" to "test-client",
                    "$PREFIX.client.secret" to "test-secret",
                    "$PREFIX.client.redirect-uri" to "http://localhost/callback",
                    "$PREFIX.service-client.id" to "test-service-client",
                    "$PREFIX.service-client.secret" to "test-service-client-secret",
                )
            }

            // then
            error.failureMessages() shouldContain "issuer"
        }

        "fails to bind when client secret is missing" {
            // when
            val error = shouldThrow<Exception> {
                bind(
                    "$PREFIX.issuer" to "http://localhost:8080",
                    "$PREFIX.jwk.file" to "target/jwk.json",
                    "$PREFIX.client.id" to "test-client",
                    "$PREFIX.client.redirect-uri" to "http://localhost/callback",
                    "$PREFIX.service-client.id" to "test-service-client",
                    "$PREFIX.service-client.secret" to "test-service-client-secret",
                )
            }

            // then
            error.failureMessages() shouldContain "secret"
        }

        "fails to bind when service client secret is missing" {
            // when
            val error = shouldThrow<Exception> {
                bind(
                    "$PREFIX.issuer" to "http://localhost:8080",
                    "$PREFIX.jwk.file" to "target/jwk.json",
                    "$PREFIX.client.id" to "test-client",
                    "$PREFIX.client.secret" to "test-secret",
                    "$PREFIX.client.redirect-uri" to "http://localhost/callback",
                    "$PREFIX.service-client.id" to "test-service-client",
                )
            }

            // then
            error.failureMessages() shouldContain "service-client"
        }
    }
})

private fun bind(vararg properties: Pair<String, String>): AuthServerProperties =
    Binder(MapConfigurationPropertySource(properties.toMap()))
        .bind(PREFIX, Bindable.of(AuthServerProperties::class.java))
        .get()

private fun Throwable.failureMessages(): String =
    generateSequence(this as Throwable?) { it.cause }
        .joinToString(" | ") { it.message.orEmpty() }
