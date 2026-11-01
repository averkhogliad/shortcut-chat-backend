package io.averkhoglyad.shortcut.outbox.autoconfigure

import io.averkhoglyad.shortcut.outbox.persistence.OutboxDao
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.time.Clock

class OutboxMetrics(
    private val dao: OutboxDao,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun bindTo(registry: MeterRegistry) {
        Gauge.builder("outbox.backlog", dao) { it.countByState("NEW").toDouble() }
            .description("Number of outbox entries waiting to be published")
            .register(registry)
        Gauge.builder("outbox.retry.wait", dao) { it.countDueRetries(clock.instant()).toDouble() }
            .description("Number of outbox entries whose retry is due")
            .register(registry)
        Gauge.builder("outbox.dead", dao) { it.countByState("DEAD").toDouble() }
            .description("Number of dead outbox entries")
            .register(registry)
        Gauge.builder("outbox.cleanup", dao) {
            (it.countByState("PUBLISHED") + it.countByState("DEAD")).toDouble()
        }
            .description("Number of terminal outbox entries eligible for cleanup")
            .register(registry)
    }
}
