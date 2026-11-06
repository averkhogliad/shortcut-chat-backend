package io.averkhoglyad.shortcut.outbox.autoconfigure

import tools.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.outbox.core.OutboxCleaner
import io.averkhoglyad.shortcut.outbox.core.OutboxEntry
import io.averkhoglyad.shortcut.outbox.core.OutboxRelay
import io.averkhoglyad.shortcut.outbox.core.OutboxWriter
import io.averkhoglyad.shortcut.outbox.messaging.OutboxPublisher
import io.averkhoglyad.shortcut.outbox.messaging.PublishResult
import io.averkhoglyad.shortcut.outbox.persistence.ClaimedOutboxEntry
import io.averkhoglyad.shortcut.outbox.persistence.OutboxDao
import io.micrometer.core.instrument.MeterRegistry
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.ConfigurationCondition
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.function.Supplier

class TxOutboxAutoConfigurationTest : FreeSpec({

    afterTest { clearAllMocks() }

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

        "registers the default publisher and the runtime when KafkaTemplate comes from Kafka auto-configuration" {
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
                // then
                context.startupFailure shouldBe null
                context.getBeanNamesForType(OutboxPublisher::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxRelay::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxCleaner::class.java).size shouldBe 1
                context.getBeanNamesForType(ThreadPoolTaskScheduler::class.java).size shouldBe 1
                context.containsBean("outboxTaskScheduler") shouldBe true
                context.getBeanNamesForType(OutboxMetrics::class.java).size shouldBe 1
                context.getBeanNamesForType(MeterRegistry::class.java).size shouldBe 1
                context.getBeanNamesForType(TxOutboxScheduleConfig::class.java).size shouldBe 1
            }
        }

        "uses a consumer-provided publisher instead of the default when KafkaTemplate comes from auto-configuration" {
            // given
            val publisher = mockk<OutboxPublisher>()
            val dao = mockk<OutboxDao>(relaxed = true)
            val entry = outboxEntry()
            every { publisher.publish(any()) } returns PublishResult.Success
            every { dao.claimBatch(any(), any(), any(), any()) } returns
                listOf(ClaimedOutboxEntry(entry, retryCount = 0)) andThen emptyList<ClaimedOutboxEntry>()
            val runner = kafkaOutboxRunner()
                .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
                .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
                .withBean(OutboxDao::class.java, Supplier { dao })
                .withBean(OutboxPublisher::class.java, Supplier { publisher })
                .withPropertyValues(
                    "tx-outbox.enabled=true",
                    "spring.kafka.bootstrap-servers=localhost:9092",
                )

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBeanNamesForType(OutboxPublisher::class.java).size shouldBe 1
                context.getBean(OutboxPublisher::class.java) shouldBe publisher
                context.containsBean("kafkaTemplateOutboxPublisher") shouldBe false
                val relay = context.getBean(OutboxRelay::class.java)
                relay.runCycle()
                val published = slot<OutboxEntry>()
                verify { publisher.publish(capture(published)) }
                published.captured shouldBe entry
            }
        }

        "activates the runtime for a consumer-provided publisher without a KafkaTemplate" {
            // given
            val publisher = mockk<OutboxPublisher>()
            val dao = mockk<OutboxDao>(relaxed = true)
            val entry = outboxEntry()
            every { publisher.publish(any()) } returns PublishResult.Success
            every { dao.claimBatch(any(), any(), any(), any()) } returns
                listOf(ClaimedOutboxEntry(entry, retryCount = 0)) andThen emptyList<ClaimedOutboxEntry>()
            val runner = outboxRunner()
                .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
                .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
                .withBean(OutboxDao::class.java, Supplier { dao })
                .withBean(OutboxPublisher::class.java, Supplier { publisher })
                .withPropertyValues("tx-outbox.enabled=true")

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBeanNamesForType(OutboxPublisher::class.java).size shouldBe 1
                context.getBean(OutboxPublisher::class.java) shouldBe publisher
                context.containsBean("kafkaTemplateOutboxPublisher") shouldBe false
                context.getBeanNamesForType(OutboxCleaner::class.java).size shouldBe 1
                context.getBeanNamesForType(ThreadPoolTaskScheduler::class.java).size shouldBe 1
                context.containsBean("outboxTaskScheduler") shouldBe true
                context.getBeanNamesForType(OutboxMetrics::class.java).size shouldBe 1
                context.getBeanNamesForType(MeterRegistry::class.java).size shouldBe 1
                context.getBeanNamesForType(TxOutboxScheduleConfig::class.java).size shouldBe 1
                val relay = context.getBean(OutboxRelay::class.java)
                relay.runCycle()
                val published = slot<OutboxEntry>()
                verify { publisher.publish(capture(published)) }
                published.captured shouldBe entry
            }
        }

        "registers only the write path when no publication capability is available" {
            // given
            val runner = outboxRunner()
                .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
                .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
                .withPropertyValues("tx-outbox.enabled=true")

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBeanNamesForType(OutboxPublisher::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxRelay::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxCleaner::class.java).size shouldBe 0
                context.getBeanNamesForType(ThreadPoolTaskScheduler::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxMetrics::class.java).size shouldBe 0
                context.getBeanNamesForType(TxOutboxScheduleConfig::class.java).size shouldBe 0
                context.getBeanNamesForType(OutboxDao::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxWriter::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxRouter::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxHeaderProvider::class.java).size shouldBe 1
                context.getBeanNamesForType(OutboxEmitter::class.java).size shouldBe 1
            }
        }

        "evaluates the publisher availability condition in the bean registration phase" {
            // given
            val condition = OutboxPublisherAvailableCondition()

            // when
            val phase = condition.configurationPhase

            // then
            Condition::class.java.isAssignableFrom(condition.javaClass) shouldBe true
            ConfigurationCondition::class.java.isAssignableFrom(condition.javaClass) shouldBe true
            phase shouldBe ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN
            val conditional = ConditionalOnOutboxPublisher::class.java.getAnnotation(Conditional::class.java)
            val wiredConditions = Conditional::class.java.getMethod("value").invoke(conditional) as Array<*>
            wiredConditions.toList() shouldBe listOf(OutboxPublisherAvailableCondition::class.java)
            val targetAnnotation = ConditionalOnOutboxPublisher::class.java.annotations
                .single { it.annotationClass.java.name == "java.lang.annotation.Target" }
            val allowedTargets = targetAnnotation.annotationClass.java
                .getMethod("value")
                .invoke(targetAnnotation) as Array<*>
            // Kotlin's AnnotationTarget.CLASS and AnnotationTarget.FUNCTION compile to TYPE and METHOD
            allowedTargets.map { (it as Enum<*>).name }.toSet() shouldBe setOf("TYPE", "METHOD")
            val retentionAnnotation = ConditionalOnOutboxPublisher::class.java.annotations
                .single { it.annotationClass.java.name == "java.lang.annotation.Retention" }
            val retention = retentionAnnotation.annotationClass.java
                .getMethod("value")
                .invoke(retentionAnnotation) as Enum<*>
            retention.name shouldBe "RUNTIME"
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
