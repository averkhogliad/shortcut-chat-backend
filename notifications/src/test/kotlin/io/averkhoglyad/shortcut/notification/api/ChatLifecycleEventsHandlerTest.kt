package io.averkhoglyad.shortcut.notification.api

import io.averkhoglyad.shortcut.notification.data.ChatCreated
import io.averkhoglyad.shortcut.notification.data.UserRef
import io.averkhoglyad.shortcut.notification.service.NotificationService
import io.kotest.core.spec.style.FreeSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.util.UUID.randomUUID

class ChatLifecycleEventsHandlerTest : FreeSpec({

    val service = mockk<NotificationService>()
    val target = ChatLifecycleEventsHandler(service)

    afterTest {
        clearAllMocks()
    }

    "handleChatLifecycleEvent" - {
        "passes known event to notification service" {
            runTest {
                // given
                val event = ChatCreated(
                    id = randomUUID(),
                    name = "chat",
                    owner = UserRef(randomUUID()),
                    members = listOf(UserRef(randomUUID())),
                    createdAt = Instant.now(),
                )
                coEvery { service.handleEvent(any()) } just Runs

                // when
                target.handleChatLifecycleEvent(event)

                // then
                coVerify(exactly = 1) { service.handleEvent(event) }
            }
        }
    }

    "handleUnknown" - {
        "does not pass unknown event to notification service" {
            runTest {
                // when
                target.handleUnknown(
                    payload = mapOf("id" to randomUUID().toString()),
                    eventNameHeader = "UnexpectedEvent",
                )

                // then
                coVerify(exactly = 0) { service.handleEvent(any()) }
            }
        }
    }
})
