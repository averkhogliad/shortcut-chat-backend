package io.averkhoglyad.shortcut.notification.it

import io.averkhoglyad.shortcut.notification.TestNotificationServiceConfiguration
import io.averkhoglyad.shortcut.notification.TestSecurityConfiguration
import io.averkhoglyad.shortcut.notification.signedToken
import io.kotest.core.spec.style.FreeSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.uuid
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The reactive security chain: the SSE subscription is rejected without a token, accepts a
 * token from the `token` query parameter, and the actuator probes stay public.
 */
@SpringBootTest(
    properties = [
        "spring.kafka.listener.auto-startup=false",
        // `notifications` has no database, but leftover Liquibase/PostgreSQL dependencies
        // switch on DataSource autoconfiguration; without a URL the context cannot start.
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration",
    ],
)
@ActiveProfiles("test")
@Import(TestSecurityConfiguration::class, TestNotificationServiceConfiguration::class)
class NotificationSecurityTest(
    context: ApplicationContext,
    private val jwtEncoder: JwtEncoder,
) : FreeSpec({

    // `springSecurity()` installs the filter that `mockJwt()` needs; a client built from
    // the context alone does not have the security test support attached.
    val client = WebTestClient.bindToApplicationContext(context)
        .apply(springSecurity())
        .build()

    "GET /subscribe" - {
        "returns 401 without a token" {
            // when
            val response = client.get()
                .uri("/subscribe")
                .exchange()

            // then
            response.expectStatus().isUnauthorized
        }

        "passes the security chain with a mock JWT" {
            // given
            val subscriberId = Arb.uuid().next()

            // when
            val response = client.mutateWith(mockJwt().jwt { it.subject(subscriberId.toString()) })
                .get()
                .uri("/subscribe")
                .exchange()

            // then
            response.expectStatus().isOk
        }

        "accepts a signed JWT from the token query parameter" {
            // given
            val subscriberId = Arb.uuid().next()
            val token = signedToken(jwtEncoder, subscriberId.toString())

            // when
            val response = client.get()
                .uri { uriBuilder ->
                    uriBuilder.path("/subscribe")
                        .queryParam("token", token)
                        .build()
                }
                .exchange()

            // then
            response.expectStatus().isOk
        }
    }

    "GET /actuator/health" - {
        "is available without a token" {
            // when
            val response = client.get()
                .uri("/actuator/health")
                .exchange()

            // then
            response.expectStatus().isOk
        }
    }
})
