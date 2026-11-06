package io.averkhoglyad.shortcut.notification.api

import io.averkhoglyad.shortcut.notification.ControlledNotificationService
import io.averkhoglyad.shortcut.notification.NotificationStreamTestConfiguration
import io.averkhoglyad.shortcut.notification.TestSecurityConfiguration
import io.averkhoglyad.shortcut.notification.data.ChatEventWithMembers
import io.averkhoglyad.shortcut.notification.data.ChatRef
import io.averkhoglyad.shortcut.notification.data.DebugEvent
import io.averkhoglyad.shortcut.notification.data.MessagePublished
import io.averkhoglyad.shortcut.notification.data.UserRef
import io.averkhoglyad.shortcut.notification.signedToken
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.flowOf
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID.randomUUID

/**
 * Subscription behaviour through the production router and security chain: the subscriber is
 * taken from the JWT `sub` claim carried in the `token` query parameter, and events remain
 * filtered and ordered exactly as before.
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
@Import(TestSecurityConfiguration::class, NotificationStreamTestConfiguration::class)
class NotificationSubscriptionHandlerTest(
    context: ApplicationContext,
    private val jwtEncoder: JwtEncoder,
    private val notificationService: ControlledNotificationService,
) : FreeSpec({

    // `springSecurity()` installs the filter that performs token resolution; without it the
    // functional router runs outside the security chain under test.
    val client = WebTestClient.bindToApplicationContext(context)
        .apply(springSecurity())
        .build()

    afterTest {
        notificationService.reset()
    }

    "subscribe" - {
        "streams only events of chats that include subscriber" {
            // given
            val subscriberId = randomUUID()
            val visibleEvent = MessagePublished(
                id = randomUUID(),
                text = "visible",
                chat = ChatRef(randomUUID()),
                author = UserRef(randomUUID()),
                createdAt = Instant.ofEpochSecond(20),
            )
            val hiddenEvent = MessagePublished(
                id = randomUUID(),
                text = "hidden",
                chat = ChatRef(randomUUID()),
                author = UserRef(randomUUID()),
                createdAt = Instant.ofEpochSecond(30),
            )
            val debugEvent = DebugEvent(
                chat = ChatRef(randomUUID()),
                createdAt = Instant.ofEpochSecond(10),
            )
            notificationService.stream = flowOf(
                listOf(
                    ChatEventWithMembers(hiddenEvent, listOf(UserRef(randomUUID()))),
                    ChatEventWithMembers(visibleEvent, listOf(UserRef(subscriberId))),
                    ChatEventWithMembers(debugEvent, emptyList()),
                )
            )
            val token = signedToken(jwtEncoder, subscriberId.toString())

            // when
            val body = client.get()
                .uri { builder -> builder.path("/subscribe").queryParam("token", token).build() }
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String::class.java)
                .responseBody
                .collectList()
                .block()
                .orEmpty()
                .joinToString("\n")

            // then
            (body.contains(visibleEvent.id.toString())) shouldBe true
            (body.contains(hiddenEvent.id.toString())) shouldBe false
            (body.indexOf("DebugEvent") < body.indexOf(visibleEvent.id.toString())) shouldBe true
        }

        "sorts events by creation time" {
            // given
            val subscriberId = randomUUID()
            val chatId = randomUUID()
            val later = MessagePublished(
                id = randomUUID(),
                text = "later",
                chat = ChatRef(chatId),
                author = UserRef(randomUUID()),
                createdAt = Instant.ofEpochSecond(20),
            )
            val earlier = MessagePublished(
                id = randomUUID(),
                text = "earlier",
                chat = ChatRef(chatId),
                author = UserRef(randomUUID()),
                createdAt = Instant.ofEpochSecond(10),
            )
            notificationService.stream = flowOf(
                listOf(
                    ChatEventWithMembers(later, listOf(UserRef(subscriberId))),
                    ChatEventWithMembers(earlier, listOf(UserRef(subscriberId))),
                )
            )
            val token = signedToken(jwtEncoder, subscriberId.toString())

            // when
            val body = client.get()
                .uri { builder -> builder.path("/subscribe").queryParam("token", token).build() }
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk
                .returnResult(String::class.java)
                .responseBody
                .collectList()
                .block()
                .orEmpty()
                .joinToString("\n")

            // then
            (body.indexOf(earlier.id.toString()) < body.indexOf(later.id.toString())) shouldBe true
        }

        "rejects request without a token" {
            // when
            val response = client.get()
                .uri("/subscribe")
                .exchange()

            // then
            response.expectStatus().isUnauthorized
        }

        "rejects a token whose subject is not a UUID" {
            // given
            val token = signedToken(jwtEncoder, "not-a-uuid")

            // when
            val response = client.get()
                .uri { builder -> builder.path("/subscribe").queryParam("token", token).build() }
                .exchange()

            // then
            response.expectStatus().isUnauthorized
        }
    }
})
