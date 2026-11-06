package io.averkhoglyad.shortcut.outbox.autoconfigure

import tools.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearAllMocks
import io.mockk.mockk
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.KafkaTemplate
import java.util.function.Supplier

class OutboxActivationReporterTest : FreeSpec({

    afterTest { clearAllMocks() }

    "OutboxActivationReporter" - {

        "warns once that the runtime is inactive when no publication capability is available" {
            // given
            val runner = outboxRunner()
                .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
                .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
                .withPropertyValues("tx-outbox.enabled=true")

            // when
            runner.run { context ->
                val warnings = captureWarnings { context.publishReadyEvent() }

                // then
                context.getBeanNamesForType(OutboxActivationReporter::class.java).size shouldBe 1
                warnings.size shouldBe 1
                warnings.single().formattedMessage shouldContain "outbox runtime is inactive"
                warnings.single().formattedMessage shouldContain "OutboxPublisher"
            }
        }

        "does not warn when the runtime is active" {
            // given
            val runner = kafkaOutboxRunner()
                .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
                .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
                .withPropertyValues(
                    "tx-outbox.enabled=true",
                    "spring.kafka.bootstrap-servers=localhost:9092",
                )

            // when
            runner.run { context ->
                val warnings = captureWarnings { context.publishReadyEvent() }

                // then
                context.getBeanNamesForType(OutboxActivationReporter::class.java).size shouldBe 1
                warnings.size shouldBe 0
            }
        }

        "warns naming ObjectMapper when ObjectMapper is missing" {
            // given
            val runner = outboxRunner()
                .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
                .withBean(KafkaTemplate::class.java, Supplier { mockk<KafkaTemplate<String, ByteArray>>(relaxed = true) })
                .withPropertyValues("tx-outbox.enabled=true")

            // when
            runner.run { context ->
                val warnings = captureWarnings { context.publishReadyEvent() }

                // then
                context.getBeanNamesForType(OutboxActivationReporter::class.java).size shouldBe 1
                warnings.size shouldBe 1
                warnings.single().formattedMessage shouldContain "outbox runtime is inactive"
                warnings.single().formattedMessage shouldContain "ObjectMapper"
            }
        }

        "warns naming JdbcClient when JdbcClient is missing" {
            // given
            val runner = outboxRunner()
                .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
                .withBean(KafkaTemplate::class.java, Supplier { mockk<KafkaTemplate<String, ByteArray>>(relaxed = true) })
                .withPropertyValues("tx-outbox.enabled=true")

            // when
            runner.run { context ->
                val warnings = captureWarnings { context.publishReadyEvent() }

                // then
                context.getBeanNamesForType(OutboxActivationReporter::class.java).size shouldBe 1
                warnings.size shouldBe 1
                warnings.single().formattedMessage shouldContain "outbox runtime is inactive"
                warnings.single().formattedMessage shouldContain "JdbcClient"
            }
        }
    }
})
