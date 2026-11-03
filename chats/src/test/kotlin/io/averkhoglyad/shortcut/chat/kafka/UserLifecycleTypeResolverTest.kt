package io.averkhoglyad.shortcut.chat.kafka

import io.averkhoglyad.shortcut.chat.core.model.UserCreatedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserDeletedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserUpdatedPayload
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.common.header.internals.RecordHeaders

class UserLifecycleTypeResolverTest : FreeSpec({

    val target = UserLifecycleTypeResolver()

    "resolveType" - {
        "returns UserCreatedPayload type for UserCreated event name" {
            // given
            val headers = headersWith("UserCreated")

            // when
            val result = target.resolveType("UserLifecycle", null, headers)

            // then
            result.rawClass shouldBe UserCreatedPayload::class.java
        }

        "returns UserUpdatedPayload type for UserUpdated event name" {
            // given
            val headers = headersWith("UserUpdated")

            // when
            val result = target.resolveType("UserLifecycle", null, headers)

            // then
            result.rawClass shouldBe UserUpdatedPayload::class.java
        }

        "returns UserDeletedPayload type for UserDeleted event name" {
            // given
            val headers = headersWith("UserDeleted")

            // when
            val result = target.resolveType("UserLifecycle", null, headers)

            // then
            result.rawClass shouldBe UserDeletedPayload::class.java
        }

        "returns Map type for unknown event name" {
            // given
            val headers = headersWith("UnexpectedEvent")

            // when
            val result = target.resolveType("UserLifecycle", null, headers)

            // then
            result.rawClass shouldBe Map::class.java
        }

        "returns Map type when event name header is absent" {
            // given
            val headers = RecordHeaders()

            // when
            val result = target.resolveType("UserLifecycle", null, headers)

            // then
            result.rawClass shouldBe Map::class.java
        }
    }
})

private const val EVENT_NAME = "X-Event-Name"

private fun headersWith(eventName: String) = RecordHeaders()
    .apply { add(EVENT_NAME, eventName.toByteArray(Charsets.UTF_8)) }
