package io.averkhoglyad.shortcut.outbox.autoconfigure

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import java.time.Duration

class TxOutboxPropertiesTest : FreeSpec({

    "TxOutboxProperties" - {

        "binds every configured group" {
            // given
            val runner = ApplicationContextRunner()
                .withUserConfiguration(PropertiesTestConfiguration::class.java)
                .withPropertyValues(
                    "tx-outbox.enabled=true",
                    "tx-outbox.channels[0].name=user-lifecycle",
                    "tx-outbox.channels[0].topic=UserLifecycle",
                    "tx-outbox.channels[1].name=mailer-commands",
                    "tx-outbox.channels[1].topic=MailerCommands",
                    "tx-outbox.routes[0].type=UserCreated",
                    "tx-outbox.routes[0].channel=user-lifecycle",
                    "tx-outbox.routes[0].event-name=UserCreated",
                    "tx-outbox.routes[0].headers.x-source=users",
                    "tx-outbox.routes[1].type=SendCreatedUserNotification",
                    "tx-outbox.routes[1].channel=mailer-commands",
                    "tx-outbox.routes[1].event-name=SendEmail",
                    "tx-outbox.relay.batch-size=42",
                    "tx-outbox.relay.max-loops-per-run=3",
                    "tx-outbox.relay.schedule.mode=fixed-delay",
                    "tx-outbox.relay.schedule.fixed-delay=PT2S",
                    "tx-outbox.relay.schedule.initial-delay=PT1S",
                    "tx-outbox.relay.schedule.zone=Europe/Moscow",
                    "tx-outbox.lease.owner=node-1",
                    "tx-outbox.lease.duration=PT45S",
                    "tx-outbox.retry.max-attempts=7",
                    "tx-outbox.retry.initial-delay=PT3S",
                    "tx-outbox.retry.multiplier=1.5",
                    "tx-outbox.retry.max-delay=PT2M",
                    "tx-outbox.cleanup.enabled=false",
                    "tx-outbox.cleanup.published-retention=P3D",
                    "tx-outbox.cleanup.dead-retention=P14D",
                    "tx-outbox.cleanup.schedule.mode=cron",
                    "tx-outbox.cleanup.schedule.cron=0 0 * * * *",
                    "tx-outbox.scheduler.enabled=false",
                )

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                val properties = context.getBean(TxOutboxProperties::class.java)

                properties.enabled shouldBe true
                properties.channels.map { it.name to it.topic } shouldBe listOf(
                    "user-lifecycle" to "UserLifecycle",
                    "mailer-commands" to "MailerCommands",
                )
                properties.routes.map { it.type to it.eventName } shouldBe listOf(
                    "UserCreated" to "UserCreated",
                    "SendCreatedUserNotification" to "SendEmail",
                )
                properties.routes.first().headers shouldBe mapOf("x-source" to "users")
                properties.relay.batchSize shouldBe 42
                properties.relay.maxLoopsPerRun shouldBe 3
                properties.relay.schedule.mode shouldBe "fixed-delay"
                properties.relay.schedule.fixedDelay shouldBe Duration.ofSeconds(2)
                properties.relay.schedule.initialDelay shouldBe Duration.ofSeconds(1)
                properties.relay.schedule.zone shouldBe "Europe/Moscow"
                properties.lease.owner shouldBe "node-1"
                properties.lease.duration shouldBe Duration.ofSeconds(45)
                properties.retry.maxAttempts shouldBe 7
                properties.retry.initialDelay shouldBe Duration.ofSeconds(3)
                properties.retry.multiplier shouldBe 1.5
                properties.retry.maxDelay shouldBe Duration.ofMinutes(2)
                properties.cleanup.enabled shouldBe false
                properties.cleanup.publishedRetention shouldBe Duration.ofDays(3)
                properties.cleanup.deadRetention shouldBe Duration.ofDays(14)
                properties.cleanup.schedule.mode shouldBe "cron"
                properties.cleanup.schedule.cron shouldBe "0 0 * * * *"
                properties.scheduler.enabled shouldBe false
            }
        }

        "applies the design defaults" {
            // given
            val runner = ApplicationContextRunner()
                .withUserConfiguration(PropertiesTestConfiguration::class.java)

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                val properties = context.getBean(TxOutboxProperties::class.java)

                properties.enabled shouldBe false
                properties.channels shouldBe emptyList()
                properties.routes shouldBe emptyList()
                properties.relay.batchSize shouldBe 100
                properties.relay.maxLoopsPerRun shouldBe 10
                properties.relay.schedule.mode shouldBe "fixed-delay"
                properties.relay.schedule.fixedDelay shouldBe Duration.ofSeconds(5)
                properties.relay.schedule.initialDelay shouldBe Duration.ofSeconds(1)
                properties.lease.duration shouldBe Duration.ofSeconds(30)
                properties.retry.maxAttempts shouldBe 10
                properties.retry.initialDelay shouldBe Duration.ofSeconds(5)
                properties.retry.multiplier shouldBe 2.0
                properties.retry.maxDelay shouldBe Duration.ofMinutes(5)
                properties.cleanup.enabled shouldBe true
                properties.cleanup.publishedRetention shouldBe Duration.ofDays(7)
                properties.cleanup.deadRetention shouldBe Duration.ofDays(30)
                properties.cleanup.schedule.mode shouldBe "cron"
                properties.scheduler.enabled shouldBe true
            }
        }

        "fails startup when a route references an undeclared channel" {
            // given
            val runner = ApplicationContextRunner()
                .withUserConfiguration(PropertiesTestConfiguration::class.java)
                .withPropertyValues(
                    "tx-outbox.channels[0].name=declared-channel",
                    "tx-outbox.channels[0].topic=SomeTopic",
                    "tx-outbox.routes[0].type=UserCreated",
                    "tx-outbox.routes[0].channel=undeclared-channel",
                    "tx-outbox.routes[0].event-name=UserCreated",
                )

            // when
            runner.run { context ->
                // then
                val failure = context.startupFailure
                failure.shouldNotBeNull()
                val messages = generateSequence(failure) { it.cause }
                    .mapNotNull { it.message }
                    .toList()
                messages.any { it.contains("undeclared-channel") } shouldBe true
            }
        }

        "fails startup when the schedule mode is unknown" {
            // given
            val runner = ApplicationContextRunner()
                .withUserConfiguration(PropertiesTestConfiguration::class.java)
                .withPropertyValues("tx-outbox.relay.schedule.mode=sometimes")

            // when
            runner.run { context ->
                // then
                val failure = context.startupFailure
                failure.shouldNotBeNull()
                val messages = generateSequence(failure) { it.cause }
                    .mapNotNull { it.message }
                    .toList()
                messages.any { it.contains("relay.schedule.mode") } shouldBe true
            }
        }
    }
})

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TxOutboxProperties::class)
class PropertiesTestConfiguration
