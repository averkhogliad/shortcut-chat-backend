package io.averkhoglyad.shortcut.outbox.autoconfigure

import io.averkhoglyad.shortcut.common.util.slf4j
import io.averkhoglyad.shortcut.outbox.core.OutboxCleaner
import io.averkhoglyad.shortcut.outbox.core.OutboxRelay
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled

@Configuration(proxyBeanMethods = false)
@Profile("!test")
@ConditionalOnProperty(
    prefix = "tx-outbox",
    name = ["scheduler.enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class TxOutboxScheduleConfig {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
        prefix = "tx-outbox.relay.schedule",
        name = ["mode"],
        havingValue = "fixed-delay",
        matchIfMissing = true,
    )
    class RelayFixedDelayScheduleConfig(
        private val relay: OutboxRelay,
    ) {

        private val logger by slf4j()

        @Scheduled(
            initialDelayString = "\${tx-outbox.relay.schedule.initial-delay:PT1S}",
            fixedDelayString = "\${tx-outbox.relay.schedule.fixed-delay:PT5S}",
            scheduler = "outboxTaskScheduler",
        )
        @SchedulerLock(
            name = "outbox-relay",
            lockAtLeastFor = "\${tx-outbox.relay.schedule.lock-at-least-for:PT1S}",
            lockAtMostFor = "\${tx-outbox.relay.schedule.lock-at-most-for:PT10M}",
        )
        fun runRelay() {
            runCatching { relay.runCycle() }
                .onFailure { logger.error("Error during outbox-relay scheduled execution", it) }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
        prefix = "tx-outbox.relay.schedule",
        name = ["mode"],
        havingValue = "cron",
    )
    class RelayCronScheduleConfig(
        private val relay: OutboxRelay,
    ) {

        private val logger by slf4j()

        @Scheduled(
            cron = "\${tx-outbox.relay.schedule.cron:*/5 * * * * *}",
            zone = "\${tx-outbox.relay.schedule.zone:}",
            scheduler = "outboxTaskScheduler",
        )
        @SchedulerLock(
            name = "outbox-relay",
            lockAtLeastFor = "\${tx-outbox.relay.schedule.lock-at-least-for:PT1S}",
            lockAtMostFor = "\${tx-outbox.relay.schedule.lock-at-most-for:PT10M}",
        )
        fun runRelay() {
            runCatching { relay.runCycle() }
                .onFailure { logger.error("Error during outbox-relay scheduled execution", it) }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
        prefix = "tx-outbox",
        name = ["cleanup.enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    class CleanupScheduleConfig {

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnProperty(
            prefix = "tx-outbox.cleanup.schedule",
            name = ["mode"],
            havingValue = "cron",
            matchIfMissing = true,
        )
        class CleanupCronScheduleConfig(
            private val cleaner: OutboxCleaner,
        ) {

            private val logger by slf4j()

            @Scheduled(
                cron = "\${tx-outbox.cleanup.schedule.cron:0 */30 * * * *}",
                zone = "\${tx-outbox.cleanup.schedule.zone:}",
                scheduler = "outboxTaskScheduler",
            )
            @SchedulerLock(
                name = "outbox-cleanup",
                lockAtLeastFor = "\${tx-outbox.cleanup.schedule.lock-at-least-for:PT1M}",
                lockAtMostFor = "\${tx-outbox.cleanup.schedule.lock-at-most-for:PT12H}",
            )
            fun runCleanup() {
                runCatching { cleaner.clean() }
                    .onFailure { logger.error("Error during outbox-cleanup scheduled execution", it) }
            }
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnProperty(
            prefix = "tx-outbox.cleanup.schedule",
            name = ["mode"],
            havingValue = "fixed-delay",
        )
        class CleanupFixedDelayScheduleConfig(
            private val cleaner: OutboxCleaner,
        ) {

            private val logger by slf4j()

            @Scheduled(
                initialDelayString = "\${tx-outbox.cleanup.schedule.initial-delay:PT1M}",
                fixedDelayString = "\${tx-outbox.cleanup.schedule.fixed-delay:PT30M}",
                scheduler = "outboxTaskScheduler",
            )
            @SchedulerLock(
                name = "outbox-cleanup",
                lockAtLeastFor = "\${tx-outbox.cleanup.schedule.lock-at-least-for:PT1M}",
                lockAtMostFor = "\${tx-outbox.cleanup.schedule.lock-at-most-for:PT12H}",
            )
            fun runCleanup() {
                runCatching { cleaner.clean() }
                    .onFailure { logger.error("Error during outbox-cleanup scheduled execution", it) }
            }
        }
    }
}
