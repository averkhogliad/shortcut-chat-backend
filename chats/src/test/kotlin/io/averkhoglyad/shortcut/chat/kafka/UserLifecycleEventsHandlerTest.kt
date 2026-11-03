package io.averkhoglyad.shortcut.chat.kafka

import io.averkhoglyad.shortcut.chat.core.model.UserCreatedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserDeletedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserUpdatedPayload
import io.averkhoglyad.shortcut.chat.core.service.UserProjectionService
import io.kotest.core.spec.style.FreeSpec
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.*

class UserLifecycleEventsHandlerTest : FreeSpec({

    val service = mockk<UserProjectionService>()
    val handler = UserLifecycleEventsHandler(service)

    afterTest {
        clearAllMocks()
    }

    "routes UserCreated to applyCreated" {
        val id = UUID.randomUUID()
        val eventTime = Instant.now()
        val payload = UserCreatedPayload(id, "name", "email@e.com")
        every { service.applyCreated(id, eventTime, payload) } returns Unit

        handler.handleCreated(payload, eventTime.toString())

        verify { service.applyCreated(id, eventTime, payload) }
    }

    "routes UserUpdated to applyUpdated" {
        val id = UUID.randomUUID()
        val eventTime = Instant.now()
        val payload = UserUpdatedPayload(id, "updated", "updated@e.com")
        every { service.applyUpdated(id, eventTime, payload) } returns Unit

        handler.handleUpdated(payload, eventTime.toString())

        verify { service.applyUpdated(id, eventTime, payload) }
    }

    "routes UserDeleted to applyDeleted" {
        val id = UUID.randomUUID()
        val eventTime = Instant.now()
        val payload = UserDeletedPayload(id)
        every { service.applyDeleted(id, eventTime, payload) } returns Unit

        handler.handleDeleted(payload, eventTime.toString())

        verify { service.applyDeleted(id, eventTime, payload) }
    }

    "default handler logs unknown event" {
        handler.handleUnknown(mapOf("foo" to "bar"), "UnknownEvent")

        verify(exactly = 0) { service.applyCreated(any(), any(), any()) }
        verify(exactly = 0) { service.applyUpdated(any(), any(), any()) }
        verify(exactly = 0) { service.applyDeleted(any(), any(), any()) }
    }
})
