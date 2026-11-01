package io.averkhoglyad.shortcut.notification.api

import io.averkhoglyad.shortcut.notification.config.WebConfig
import io.averkhoglyad.shortcut.notification.data.ChatEventWithMembers
import io.averkhoglyad.shortcut.notification.data.ChatRef
import io.averkhoglyad.shortcut.notification.data.DebugEvent
import io.averkhoglyad.shortcut.notification.data.MessagePublished
import io.averkhoglyad.shortcut.notification.data.UserRef
import io.averkhoglyad.shortcut.notification.service.NotificationService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.springframework.http.MediaType
import org.springframework.mock.web.reactive.function.server.MockServerRequest
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID.randomUUID

class NotificationSubscriptionHandlerTest : FreeSpec({

    val service = mockk<NotificationService>()
    val target = NotificationSubscriptionHandler(service)
    val client = WebTestClient
        .bindToRouterFunction(WebConfig(target).routes())
        .build()

    afterTest {
        clearAllMocks()
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
            every { service.events() } returns flowOf(
                listOf(
                    ChatEventWithMembers(hiddenEvent, listOf(UserRef(randomUUID()))),
                    ChatEventWithMembers(visibleEvent, listOf(UserRef(subscriberId))),
                    ChatEventWithMembers(debugEvent, emptyList()),
                )
            )

            // when
            val body = client.get()
                .uri("/subscribe?userId=$subscriberId")
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
            every { service.events() } returns flowOf(
                listOf(
                    ChatEventWithMembers(later, listOf(UserRef(subscriberId))),
                    ChatEventWithMembers(earlier, listOf(UserRef(subscriberId))),
                )
            )

            // when
            val body = client.get()
                .uri("/subscribe?userId=$subscriberId")
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

        "rejects request without user id" {
            // given
            val request = MockServerRequest.builder().build()

            // when
            val error = shouldThrow<IllegalArgumentException> {
                target.subscribe(request)
            }

            // then
            error.message shouldBe "User ID header must be passed"
        }
    }
})
