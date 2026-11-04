package io.averkhoglyad.shortcut.outbox.autoconfigure

import tools.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.KafkaTemplate
import java.util.function.Supplier

class OutboxMetricsTest : FreeSpec({

    "OutboxMetrics" - {

        "binds outbox gauges to a provided MeterRegistry" {
            // given
            val registry = SimpleMeterRegistry()
            val runner = metricsRunner()
                .withBean(MeterRegistry::class.java, Supplier { registry })

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBeansOfType(MeterRegistry::class.java).size shouldBe 1
                registry.find("outbox.backlog").gauge().shouldNotBeNull()
                registry.find("outbox.retry.wait").gauge().shouldNotBeNull()
                registry.find("outbox.dead").gauge().shouldNotBeNull()
                registry.find("outbox.cleanup").gauge().shouldNotBeNull()
            }
        }

        "falls back to SimpleMeterRegistry when no MeterRegistry is present" {
            // when
            metricsRunner().run { context ->
                // then
                context.startupFailure shouldBe null
                val registry = context.getBean(MeterRegistry::class.java)
                registry::class shouldBe SimpleMeterRegistry::class
                context.getBeansOfType(MeterRegistry::class.java).size shouldBe 1
                registry.find("outbox.backlog").gauge().shouldNotBeNull()
                registry.find("outbox.retry.wait").gauge().shouldNotBeNull()
                registry.find("outbox.dead").gauge().shouldNotBeNull()
                registry.find("outbox.cleanup").gauge().shouldNotBeNull()
            }
        }
    }
})

private fun metricsRunner(): ApplicationContextRunner =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TxOutboxAutoConfiguration::class.java))
        .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
        .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
        .withBean(KafkaTemplate::class.java, Supplier { mockk<KafkaTemplate<String, ByteArray>>(relaxed = true) })
        .withPropertyValues("tx-outbox.enabled=true")
