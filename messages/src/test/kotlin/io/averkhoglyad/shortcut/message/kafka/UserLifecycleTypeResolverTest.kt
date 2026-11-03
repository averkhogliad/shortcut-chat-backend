package io.averkhoglyad.shortcut.message.kafka

import io.averkhoglyad.shortcut.message.core.model.UserCreatedPayload
import io.averkhoglyad.shortcut.message.core.model.UserDeletedPayload
import io.averkhoglyad.shortcut.message.core.model.UserUpdatedPayload
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.common.header.internals.RecordHeaders

class UserLifecycleTypeResolverTest : FreeSpec({

    val target = UserLifecycleTypeResolver()

    "resolveType" - {
        "returns UserCreatedPayload type for UserCreated event name" {
            val headers = headersWith("UserCreated")

            val result = target.resolveType("UserLifecycle", null, headers)

            result.rawClass shouldBe UserCreatedPayload::class.java
        }

        "returns UserUpdatedPayload type for UserUpdated event name" {
            val headers = headersWith("UserUpdated")

            val result = target.resolveType("UserLifecycle", null, headers)

            result.rawClass shouldBe UserUpdatedPayload::class.java
        }

        "returns UserDeletedPayload type for UserDeleted event name" {
            val headers = headersWith("UserDeleted")

            val result = target.resolveType("UserLifecycle", null, headers)

            result.rawClass shouldBe UserDeletedPayload::class.java
        }

        "returns Map type for unknown event name" {
            val headers = headersWith("UnexpectedEvent")

            val result = target.resolveType("UserLifecycle", null, headers)

            result.rawClass shouldBe Map::class.java
        }

        "returns Map type when event name header is absent" {
            val headers = RecordHeaders()

            val result = target.resolveType("UserLifecycle", null, headers)

            result.rawClass shouldBe Map::class.java
        }
    }
})

private const val EVENT_NAME = "X-Event-Name"

private fun headersWith(eventName: String) = RecordHeaders()
    .apply { add(EVENT_NAME, eventName.toByteArray(Charsets.UTF_8)) }
