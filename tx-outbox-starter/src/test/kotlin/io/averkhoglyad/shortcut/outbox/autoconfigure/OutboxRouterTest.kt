package io.averkhoglyad.shortcut.outbox.autoconfigure

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class OutboxRouterTest : FreeSpec({

    "route" - {

        "resolves a configured type to the channel topic and event name" {
            // given
            val target = ConfigurationBackedOutboxRouter(
                channels = listOf(
                    channel(name = "user-lifecycle", topic = "UserLifecycle"),
                    channel(name = "mailer-commands", topic = "MailerCommands"),
                ),
                routes = listOf(
                    route(
                        type = "SendCreatedUserNotification",
                        channel = "mailer-commands",
                        eventName = "SendEmail",
                        headers = mapOf("x-source" to "users"),
                    ),
                ),
            )

            // when
            val actual = target.route("SendCreatedUserNotification")

            // then
            actual shouldBe Route(
                type = "SendCreatedUserNotification",
                channel = "mailer-commands",
                topic = "MailerCommands",
                eventName = "SendEmail",
                headers = mapOf("x-source" to "users"),
            )
        }

        "rejects a route that references an undeclared channel" {
            // given
            val channels = listOf(channel(name = "user-lifecycle", topic = "UserLifecycle"))
            val routes = listOf(route(channel = "missing-channel"))

            // when
            val error = shouldThrow<IllegalStateException> {
                ConfigurationBackedOutboxRouter(channels, routes)
            }

            // then
            error.message shouldBe "Outbox route 'UserCreated' references undeclared channel 'missing-channel'"
        }

        "rejects a type without a configured route" {
            // given
            val target = ConfigurationBackedOutboxRouter(emptyMap())

            // when
            val error = shouldThrow<IllegalStateException> {
                target.route("UnknownType")
            }

            // then
            error.message shouldBe "No outbox route configured for message type 'UnknownType'"
        }
    }
})
