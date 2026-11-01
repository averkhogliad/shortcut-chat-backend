package io.averkhoglyad.shortcut.message.core.output

import io.averkhoglyad.shortcut.message.core.model.ChatRef
import io.averkhoglyad.shortcut.message.core.model.Message
import io.averkhoglyad.shortcut.message.core.model.UserRef
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.apache.kafka.common.header.Headers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.UUID.randomUUID

class MessageCreatedMessageFactoryImplTest : FreeSpec({

    val now = Instant.parse("2026-01-01T00:00:00Z")
    val clock = Clock.fixed(now, ZoneOffset.UTC)
    val target = MessageCreatedMessageFactoryImpl(
        topic = "ChatLifecycle",
        version = "v1",
        eventName = "MessagePublished",
        applicationName = "messages-app",
        clock = clock,
    )

    "create" - {
        "builds producer record with topic, key, payload and headers" {
            // given
            val message = message()

            // when
            val record = target.create(message)

            // then
            record.topic() shouldBe "ChatLifecycle"
            record.key() shouldBe message.chat.id.toString()
            record.value() shouldBeSameInstanceAs message
            record.headers().verifyHeaders("MessagePublished")
            val eventId = record.headers().headerAsString("X-Event-Id").shouldNotBeNull()
            UUID.fromString(eventId)
        }

        "generates unique event id for every record" {
            // given
            val message = message()

            // when
            val first = target.create(message).headers().headerAsString("X-Event-Id")
            val second = target.create(message).headers().headerAsString("X-Event-Id")

            // then
            first shouldNotBe second
        }
    }
})

private fun message() = Message(
    id = randomUUID(),
    text = "text",
    chat = ChatRef(randomUUID()),
    author = UserRef(randomUUID()),
    createdAt = Instant.parse("2026-01-01T00:00:00Z"),
)

private fun Headers.verifyHeaders(eventName: String) {
    headerAsString("X-Event-Name") shouldBe eventName
    headerAsString("X-Event-Version") shouldBe "v1"
    headerAsString("X-Published-At") shouldBe "2026-01-01T00:00:00Z"
    headerAsString("X-Published-By") shouldBe "messages-app"
}

private fun Headers.headerAsString(name: String): String? =
    lastHeader(name)?.value()?.toString(Charsets.UTF_8)
