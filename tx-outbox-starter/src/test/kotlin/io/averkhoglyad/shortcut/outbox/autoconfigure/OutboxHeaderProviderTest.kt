package io.averkhoglyad.shortcut.outbox.autoconfigure

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class OutboxHeaderProviderTest : FreeSpec({

    val target = DefaultOutboxHeaderProvider()
    val entryId = UUID.fromString("018f0e3a-7c2b-7c3d-8f10-2b4c5d6e7f80")
    val now = Instant.parse("2026-01-01T00:00:00Z")
    val route = Route(
        type = "UserCreated",
        channel = "user-lifecycle",
        topic = "UserLifecycle",
        eventName = "UserCreated",
    )

    "headersFor" - {

        "sets the five standard headers" {
            // when
            val headers = target.headersFor("UserCreated", "v1", route, entryId, now, "users-app")

            // then
            headers shouldBe mapOf(
                OutboxHeaderNames.EVENT_ID to entryId.toString(),
                OutboxHeaderNames.EVENT_NAME to "UserCreated",
                OutboxHeaderNames.EVENT_VERSION to "v1",
                OutboxHeaderNames.PUBLISHED_AT to "2026-01-01T00:00:00Z",
                OutboxHeaderNames.PUBLISHED_BY to "users-app",
            )
        }

        "merges per-route static headers as extra keys" {
            // given
            val withHeaders = route.copy(
                headers = mapOf("x-tenant" to "acme", "x-source" to "users"),
            )

            // when
            val headers = target.headersFor("UserCreated", "v1", withHeaders, entryId, now, "users-app")

            // then
            headers.size shouldBe 7
            headers shouldContain (OutboxHeaderNames.EVENT_ID to entryId.toString())
            headers shouldContain ("x-tenant" to "acme")
            headers shouldContain ("x-source" to "users")
        }

        "does not let static headers overwrite the standard headers" {
            // given
            val withHeaders = route.copy(
                headers = mapOf(
                    OutboxHeaderNames.EVENT_ID to "spoofed-id",
                    OutboxHeaderNames.EVENT_NAME to "SpoofedName",
                    OutboxHeaderNames.EVENT_VERSION to "spoofed-version",
                    OutboxHeaderNames.PUBLISHED_AT to "spoofed-time",
                    OutboxHeaderNames.PUBLISHED_BY to "spoofed-app",
                    "x-tenant" to "acme",
                ),
            )

            // when
            val headers = target.headersFor("UserCreated", "v1", withHeaders, entryId, now, "users-app")

            // then
            headers.size shouldBe 6
            headers shouldContain (OutboxHeaderNames.EVENT_ID to entryId.toString())
            headers shouldContain (OutboxHeaderNames.EVENT_NAME to "UserCreated")
            headers shouldContain (OutboxHeaderNames.EVENT_VERSION to "v1")
            headers shouldContain (OutboxHeaderNames.PUBLISHED_AT to "2026-01-01T00:00:00Z")
            headers shouldContain (OutboxHeaderNames.PUBLISHED_BY to "users-app")
            headers shouldContain ("x-tenant" to "acme")
        }
    }
})
