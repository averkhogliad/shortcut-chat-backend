package io.averkhoglyad.shortcut.outbox.autoconfigure

import tools.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import java.util.function.Supplier

class TxOutboxScheduleConfigTest : FreeSpec({

    "TxOutboxScheduleConfig" - {

        "is excluded under the test profile" {
            // given
            val runner = runtimeRunner()
                .withInitializer(
                    ApplicationContextInitializer<ConfigurableApplicationContext> { context ->
                        context.environment.setActiveProfiles("test")
                    }
                )

            // when
            runner.run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBeanNamesForType(TxOutboxScheduleConfig::class.java).size shouldBe 0
            }
        }

        "is registered outside the test profile" {
            // when
            runtimeRunner().run { context ->
                // then
                context.startupFailure shouldBe null
                context.getBeanNamesForType(TxOutboxScheduleConfig::class.java).size shouldBe 1
            }
        }

        "declares relay and cleanup schedules on the outbox task scheduler" {
            // given
            val relayFixedDelay = TxOutboxScheduleConfig.RelayFixedDelayScheduleConfig::class.java
                .getDeclaredMethod("runRelay")
            val relayCron = TxOutboxScheduleConfig.RelayCronScheduleConfig::class.java
                .getDeclaredMethod("runRelay")
            val cleanupCron = TxOutboxScheduleConfig.CleanupScheduleConfig.CleanupCronScheduleConfig::class.java
                .getDeclaredMethod("runCleanup")
            val cleanupFixedDelay = TxOutboxScheduleConfig.CleanupScheduleConfig.CleanupFixedDelayScheduleConfig::class.java
                .getDeclaredMethod("runCleanup")

            // then
            relayFixedDelay.getAnnotation(Scheduled::class.java).scheduler shouldBe "outboxTaskScheduler"
            relayFixedDelay.getAnnotation(Scheduled::class.java).fixedDelayString shouldBe
                "\${tx-outbox.relay.schedule.fixed-delay:PT5S}"
            relayFixedDelay.getAnnotation(Scheduled::class.java).initialDelayString shouldBe
                "\${tx-outbox.relay.schedule.initial-delay:PT1S}"
            relayFixedDelay.getAnnotation(SchedulerLock::class.java).name shouldBe "outbox-relay"

            relayCron.getAnnotation(Scheduled::class.java).cron shouldBe
                "\${tx-outbox.relay.schedule.cron:*/5 * * * * *}"
            relayCron.getAnnotation(SchedulerLock::class.java).name shouldBe "outbox-relay"

            cleanupCron.getAnnotation(Scheduled::class.java).cron shouldBe
                "\${tx-outbox.cleanup.schedule.cron:0 */30 * * * *}"
            cleanupCron.getAnnotation(SchedulerLock::class.java).name shouldBe "outbox-cleanup"

            cleanupFixedDelay.getAnnotation(Scheduled::class.java).fixedDelayString shouldBe
                "\${tx-outbox.cleanup.schedule.fixed-delay:PT30M}"
            cleanupFixedDelay.getAnnotation(SchedulerLock::class.java).name shouldBe "outbox-cleanup"
        }
    }
})

private fun runtimeRunner(): ApplicationContextRunner =
    ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TxOutboxAutoConfiguration::class.java))
        .withBean(JdbcClient::class.java, Supplier { mockk<JdbcClient>(relaxed = true) })
        .withBean(ObjectMapper::class.java, Supplier { ObjectMapper() })
        .withBean(KafkaTemplate::class.java, Supplier { mockk<KafkaTemplate<String, ByteArray>>(relaxed = true) })
        .withPropertyValues("tx-outbox.enabled=true")
