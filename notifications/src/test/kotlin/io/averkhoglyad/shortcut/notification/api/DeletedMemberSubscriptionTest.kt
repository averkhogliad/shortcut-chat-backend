package io.averkhoglyad.shortcut.notification.api

import io.averkhoglyad.shortcut.notification.data.ChatEventWithMembers
import io.averkhoglyad.shortcut.notification.data.ChatLifecycleEvent
import io.averkhoglyad.shortcut.notification.data.ChatRef
import io.averkhoglyad.shortcut.notification.data.MessagePublished
import io.averkhoglyad.shortcut.notification.data.UserRef
import io.averkhoglyad.shortcut.notification.integration.ChatRepository
import io.averkhoglyad.shortcut.notification.service.NotificationServiceImpl
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

/**
 * Pins the consequence of the `chats` default for `GET /members`: a deleted user is absent from
 * the returned member list, so `notifications` must not treat that user as the chat audience.
 * The repository stub returns exactly what `chats` returns by default (active members only).
 */
class DeletedMemberSubscriptionTest : FreeSpec({

    val chatRepository = mockk<ChatRepository>()
    val service = NotificationServiceImpl(chatRepository, Duration.ofMillis(50))

    afterTest {
        clearAllMocks()
    }

    "subscription audience" - {
        "excludes a member absent from the chat members list (deleted user filtered by chats)" {
            runTest {
                // given
                val chatId = randomUUID()
                val deletedMemberId = randomUUID()
                val activeMemberId = randomUUID()
                val event = messagePublished(chatId)
                every { chatRepository.members(listOf(chatId)) } returns flowOf(
                    mapOf(chatId to listOf(UserRef(activeMemberId)))
                )

                // when
                val audience = receiveAudience(service, event)

                // then
                audience.visibleTo(deletedMemberId) shouldBe emptyList()
            }
        }

        "includes an active member of the same chat" {
            runTest {
                // given
                val chatId = randomUUID()
                val deletedMemberId = randomUUID()
                val activeMemberId = randomUUID()
                val event = messagePublished(chatId)
                every { chatRepository.members(listOf(chatId)) } returns flowOf(
                    mapOf(chatId to listOf(UserRef(activeMemberId)))
                )

                // when
                val audience = receiveAudience(service, event)

                // then
                audience.visibleTo(deletedMemberId) shouldBe emptyList()
                audience.visibleTo(activeMemberId) shouldBe listOf<ChatLifecycleEvent>(event)
            }
        }
    }
})

private suspend fun CoroutineScope.receiveAudience(
    service: NotificationServiceImpl,
    event: MessagePublished,
): List<ChatEventWithMembers> {
    val events = async { service.events().first() }
    yield() // let the collector subscribe to the shared flow first
    launch { service.handleEvent(event) }
    return events.await()
}

private fun messagePublished(chatId: UUID) = MessagePublished(
    id = randomUUID(),
    text = "message",
    chat = ChatRef(chatId),
    author = UserRef(randomUUID()),
    createdAt = Instant.now(),
)
