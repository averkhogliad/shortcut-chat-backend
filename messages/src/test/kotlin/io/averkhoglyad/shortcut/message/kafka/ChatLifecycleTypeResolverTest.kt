package io.averkhoglyad.shortcut.message.kafka

import io.averkhoglyad.shortcut.message.core.model.ChatCreatedPayload
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.common.header.internals.RecordHeaders

class ChatLifecycleTypeResolverTest : FreeSpec({

    val target = ChatLifecycleTypeResolver()

    "resolveType" - {
        "returns ChatCreatedPayload type for ChatCreated event name" {
            val headers = headersWith("ChatCreated")

            val result = target.resolveType("ChatLifecycle", ByteArray(0), headers)

            result.rawClass shouldBe ChatCreatedPayload::class.java
        }

        "returns Map type for unknown event name" {
            val headers = headersWith("MessagePublished")

            val result = target.resolveType("ChatLifecycle", ByteArray(0), headers)

            result.rawClass shouldBe Map::class.java
        }

        "returns Map type when event name header is absent" {
            val headers = RecordHeaders()

            val result = target.resolveType("ChatLifecycle", ByteArray(0), headers)

            result.rawClass shouldBe Map::class.java
        }
    }
})

private const val EVENT_NAME = "X-Event-Name"

private fun headersWith(eventName: String) = RecordHeaders()
    .apply { add(EVENT_NAME, eventName.toByteArray(Charsets.UTF_8)) }
