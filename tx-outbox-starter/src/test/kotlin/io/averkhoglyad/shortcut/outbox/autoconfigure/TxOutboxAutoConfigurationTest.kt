package io.averkhoglyad.shortcut.outbox.autoconfigure

import tools.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.outbox.core.OutboxCleaner
import io.averkhoglyad.shortcut.outbox.core.OutboxRelay
import io.averkhoglyad.shortcut.outbox.core.OutboxWriter
import io.averkhoglyad.shortcut.outbox.messaging.OutboxPublisher
import io.averkhoglyad.shortcut.outbox.persistence.OutboxDao
import io.micrometer.core.instrument.MeterRegistry
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.function.Supplier

class TxOutboxAutoConfigurationTest : FreeSpec({

    "TxOutboxAutoConfiguration" - {

        "registers the write path and the runtime when enabled with required beans" {
            // given
            val runner = outboxRunner()
                .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
                .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
                .withBean(KafkaTemplate::class.java, Supplier { mockk<KafkaTemplate<String, ByteArray>>(relaxed = true) })
                .withPropertyValues("tx-outbox.enabled=true")

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBeanNamesForType(OutboxDao::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxWriter::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxRouter::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxHeaderProvider::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxEmitter::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxPublisher::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxRelay::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxCleaner::class.java).size shouldBe 1
                context.getBeanNamesForType(ThreadPoolTaskScheduler::class.java).size shouldBe 1
                context.containsBean("outboxTaskScheduler") shouldBe true
                context.getBeanNamesForType(OutboxMetrics::class.java).size shouldBe 1
                context.getBeanNamesForType(MeterRegistry::class.java).size shouldBe 1
            }
        }

        "registers no runtime beans when disabled" {
            // given
            val runner = outboxRunner()
                .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
                .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
                .withBean(KafkaTemplate::class.java, Supplier { mockk<KafkaTemplate<String, ByteArray>>(relaxed = true) })
                .withPropertyValues("tx-outbox.enabled=false")

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBeanNamesForType(OutboxDao::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxWriter::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxEmitter::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxPublisher::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxRelay::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxCleaner::class.java).size shouldBe 0
                context.getBeanNamesForType(ThreadPoolTaskScheduler::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxMetrics::class.java).size shouldBe 0
                context.getBeanNamesForType(TxOutboxScheduleConfig::class.java).size shouldBe 0
            }
        }

        "registers no outbox beans when JdbcClient is missing" {
            // given
            val runner = outboxRunner()
                .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
                .withBean(KafkaTemplate::class.java, Supplier { mockk<KafkaTemplate<String, ByteArray>>(relaxed = true) })
                .withPropertyValues("tx-outbox.enabled=true")

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBeanNamesForType(OutboxDao::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxEmitter::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxPublisher::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxRelay::class.java).size shouldBe 0
            }
        }

        "registers no outbox beans when ObjectMapper is missing" {
            // given
            val runner = outboxRunner()
                .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
                .withBean(KafkaTemplate::class.java, Supplier { mockk<KafkaTemplate<String, ByteArray>>(relaxed = true) })
                .withPropertyValues("tx-outbox.enabled=true")

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBeanNamesForType(OutboxDao::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxEmitter::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxPublisher::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxRelay::class.java).size shouldBe 0
            }
        }

        "uses consumer-provided router, header provider, emitter and publisher" {
            // given
            val customRouter = mockk<OutboxRouter>()
            val customHeaderProvider = mockk<OutboxHeaderProvider>()
            val customEmitter = mockk<OutboxEmitter>()
            val customPublisher = mockk<OutboxPublisher>()
            val runner = outboxRunner()
                .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
                .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
                .withBean(KafkaTemplate::class.java, Supplier { mockk<KafkaTemplate<String, ByteArray>>(relaxed = true) })
                .withBean(OutboxRouter::class.java, Supplier { customRouter })
                .withBean(OutboxHeaderProvider::class.java, Supplier { customHeaderProvider })
                .withBean(OutboxEmitter::class.java, Supplier { customEmitter })
                .withBean(OutboxPublisher::class.java, Supplier { customPublisher })
                .withPropertyValues("tx-outbox.enabled=true")

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBean(OutboxRouter::class.java) shouldBe customRouter
                context.getBean(OutboxHeaderProvider::class.java) shouldBe customHeaderProvider
                context.getBean(OutboxEmitter::class.java) shouldBe customEmitter
                context.getBean(OutboxPublisher::class.java) shouldBe customPublisher
                context.getBeanNamesForType(OutboxRouter::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxHeaderProvider::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxEmitter::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxPublisher::class.java).size shouldBe 1
            }
        }

        "uses a consumer-provided outboxTaskScheduler" {
            // given
            val customScheduler = ThreadPoolTaskScheduler()
            val runner = outboxRunner()
                .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
                .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
                .withBean(KafkaTemplate::class.java, Supplier { mockk<KafkaTemplate<String, ByteArray>>(relaxed = true) })
                .withBean("outboxTaskScheduler", ThreadPoolTaskScheduler::class.java, Supplier { customScheduler })
                .withPropertyValues("tx-outbox.enabled=true")

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBean("outboxTaskScheduler") shouldBe customScheduler
                context.getBeanNamesForType(ThreadPoolTaskScheduler::class.java).size shouldBe 1
            }
        }
    }
})

private fun outboxRunner(): ApplicationContextRunner =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TxOutboxAutoConfiguration::class.java))
