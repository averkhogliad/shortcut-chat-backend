package io.averkhoglyad.shortcut.notification.kafka

import io.averkhoglyad.shortcut.notification.data.ChatCreated
import io.averkhoglyad.shortcut.notification.data.MessagePublished
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.common.header.internals.RecordHeaders

class ChatLifecycleTypeResolverTest : FreeSpec({

    val target = ChatLifecycleTypeResolver()

    "resolveType" - {
        "returns ChatCreated type for ChatCreated event name" {
            // given
            val headers = headersWith("ChatCreated")

            // when
            val result = target.resolveType("ChatLifecycle", ByteArray(0), headers)

            // then
            result.rawClass shouldBe ChatCreated::class.java
        }

        "returns MessagePublished type for MessagePublished event name" {
            // given
            val headers = headersWith("MessagePublished")

            // when
            val result = target.resolveType("ChatLifecycle", ByteArray(0), headers)

            // then
            result.rawClass shouldBe MessagePublished::class.java
        }

        "returns Map type for unknown event name" {
            // given
            val headers = headersWith("UnexpectedEvent")

            // when
            val result = target.resolveType("ChatLifecycle", ByteArray(0), headers)

            // then
            result.rawClass shouldBe Map::class.java
        }

        "returns Map type when event name header is absent" {
            // given
            val headers = RecordHeaders()

            // when
            val result = target.resolveType("ChatLifecycle", ByteArray(0), headers)

            // then
            result.rawClass shouldBe Map::class.java
        }
    }
})

private fun headersWith(eventName: String) = RecordHeaders()
    .apply { add(EVENT_NAME, eventName.toByteArray(Charsets.UTF_8)) }
