package io.averkhoglyad.shortcut.outbox.autoconfigure

import tools.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.outbox.core.OutboxEntry
import io.averkhoglyad.shortcut.outbox.core.OutboxWriter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OutboxEmitterTest : FreeSpec({

    val writer = mockk<OutboxWriter>()
    val objectMapper = ObjectMapper()
    val now = Instant.parse("2026-01-01T00:00:00Z")
    val clock = Clock.fixed(now, ZoneOffset.UTC)
    val router = ConfigurationBackedOutboxRouter(
        channels = listOf(
            channel(name = "mailer-commands", topic = "MailerCommands"),
            channel(name = "user-lifecycle", topic = "UserLifecycle"),
        ),
        routes = listOf(
            route(
                type = "SendCreatedUserNotification",
                channel = "mailer-commands",
                eventName = "SendEmail",
            ),
            route(
                type = "UserCreated",
                channel = "user-lifecycle",
                eventName = "UserCreated",
            ),
        ),
    )
    val target = OutboxEmitterImpl(
        router = router,
        headerProvider = DefaultOutboxHeaderProvider(),
        writer = writer,
        objectMapper = objectMapper,
        applicationName = "users-app",
        clock = clock,
    )

    afterTest {
        clearAllMocks()
    }

    "emit" - {

        "writes an entry with the resolved topic, body, headers and a UUID v7 id" {
            // given
            every { writer.write(any()) } just Runs
            val payload = mapOf("to" to "user@example.com", "subject" to "Welcome")

            // when
            target.emit("SendCreatedUserNotification", "v1", "user-42", payload)

            // then
            val entrySlot = slot<OutboxEntry>()
            verify(exactly = 1) { writer.write(capture(entrySlot)) }

            val entry = entrySlot.captured
            entry.topic shouldBe "MailerCommands"
            entry.key shouldBe "user-42"
            entry.createdAt shouldBe now
            entry.id.shouldNotBeNull()
            entry.id!!.version() shouldBe 7
            objectMapper.readTree(entry.body) shouldBe objectMapper.readTree(
                """{"to":"user@example.com","subject":"Welcome"}"""
            )
            entry.headers shouldBe mapOf(
                OutboxHeaderNames.EVENT_ID to entry.id.toString(),
                OutboxHeaderNames.EVENT_NAME to "SendEmail",
                OutboxHeaderNames.EVENT_VERSION to "v1",
                OutboxHeaderNames.PUBLISHED_AT to "2026-01-01T00:00:00Z",
                OutboxHeaderNames.PUBLISHED_BY to "users-app",
            )
        }

        "rejects an unmapped type without writing an entry" {
            // when
            val error = shouldThrow<IllegalStateException> {
                target.emit("UnknownType", "v1", null, mapOf("a" to 1))
            }

            // then
            error.message shouldBe "No outbox route configured for message type 'UnknownType'"
            verify(exactly = 0) { writer.write(any()) }
        }
    }
})
