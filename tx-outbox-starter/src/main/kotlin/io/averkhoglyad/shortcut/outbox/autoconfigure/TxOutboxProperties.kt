package io.averkhoglyad.shortcut.outbox.autoconfigure

import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("tx-outbox")
class TxOutboxProperties : InitializingBean {

    var enabled: Boolean = false
    var channels: List<Channel> = emptyList()
    var routes: List<Route> = emptyList()
    var relay: Relay = Relay()
    var lease: Lease = Lease()
    var retry: Retry = Retry()
    var cleanup: Cleanup = Cleanup()
    var scheduler: Scheduler = Scheduler()

    override fun afterPropertiesSet() {
        validate()
    }

    private fun validate() {
        val channelNames = channels.map { it.name }
        require(channelNames.all { it.isNotBlank() }) { "Every tx-outbox channel must have a non-blank name" }
        require(channelNames.distinct().size == channelNames.size) { "Duplicate tx-outbox channel names: $channelNames" }
        require(channels.all { it.topic.isNotBlank() }) { "Every tx-outbox channel must have a non-blank topic" }

        val routeTypes = routes.map { it.type }
        require(routeTypes.all { it.isNotBlank() }) { "Every tx-outbox route must have a non-blank type" }
        require(routeTypes.distinct().size == routeTypes.size) { "Duplicate tx-outbox route types: $routeTypes" }
        routes.forEach { route ->
            require(route.channel.isNotBlank()) { "Tx-outbox route '${route.type}' must declare a channel" }
            require(route.channel in channelNames) {
                "Tx-outbox route '${route.type}' references undeclared channel '${route.channel}'"
            }
            require(route.eventName.isNotBlank()) { "Tx-outbox route '${route.type}' must declare an event name" }
        }

        require(relay.batchSize > 0) { "tx-outbox.relay.batch-size must be positive" }
        require(relay.maxLoopsPerRun > 0) { "tx-outbox.relay.max-loops-per-run must be positive" }
        requireNotNegative(lease.duration, "tx-outbox.lease.duration")
        require(lease.duration != Duration.ZERO) { "tx-outbox.lease.duration must be positive" }
        require(retry.maxAttempts > 0) { "tx-outbox.retry.max-attempts must be positive" }
        requireNotNegative(retry.initialDelay, "tx-outbox.retry.initial-delay")
        require(retry.multiplier >= 1.0) { "tx-outbox.retry.multiplier must be at least 1" }
        requireNotNegative(retry.maxDelay, "tx-outbox.retry.max-delay")
        requireNotNegative(cleanup.publishedRetention, "tx-outbox.cleanup.published-retention")
        requireNotNegative(cleanup.deadRetention, "tx-outbox.cleanup.dead-retention")
        require(relay.schedule.mode in SCHEDULE_MODES) {
            "tx-outbox.relay.schedule.mode must be one of $SCHEDULE_MODES"
        }
        require(cleanup.schedule.mode in SCHEDULE_MODES) {
            "tx-outbox.cleanup.schedule.mode must be one of $SCHEDULE_MODES"
        }
        if (relay.schedule.mode == "cron") {
            require(relay.schedule.cron.isNotBlank()) { "tx-outbox.relay.schedule.cron must not be blank" }
        }
        if (cleanup.schedule.mode == "cron") {
            require(cleanup.schedule.cron.isNotBlank()) { "tx-outbox.cleanup.schedule.cron must not be blank" }
        }
        if (relay.schedule.mode == "fixed-delay") {
            requireNotNegative(relay.schedule.fixedDelay, "tx-outbox.relay.schedule.fixed-delay")
            requireNotNegative(relay.schedule.initialDelay, "tx-outbox.relay.schedule.initial-delay")
        }
        if (cleanup.schedule.mode == "fixed-delay") {
            requireNotNegative(cleanup.schedule.fixedDelay, "tx-outbox.cleanup.schedule.fixed-delay")
            requireNotNegative(cleanup.schedule.initialDelay, "tx-outbox.cleanup.schedule.initial-delay")
        }
    }

    private fun requireNotNegative(value: Duration, name: String) {
        require(!value.isNegative) { "$name must not be negative" }
    }

    class Channel {
        var name: String = ""
        var topic: String = ""
    }

    class Route {
        var type: String = ""
        var channel: String = ""
        var eventName: String = ""
        var headers: Map<String, String> = emptyMap()
    }

    class Relay {
        var batchSize: Int = 100
        var maxLoopsPerRun: Int = 10
        var schedule: Schedule = Schedule()
    }

    class Lease {
        var owner: String = ""
        var duration: Duration = Duration.ofSeconds(30)
    }

    class Retry {
        var maxAttempts: Int = 10
        var initialDelay: Duration = Duration.ofSeconds(5)
        var multiplier: Double = 2.0
        var maxDelay: Duration = Duration.ofMinutes(5)
    }

    class Cleanup {
        var enabled: Boolean = true
        var publishedRetention: Duration = Duration.ofDays(7)
        var deadRetention: Duration = Duration.ofDays(30)
        var schedule: Schedule = Schedule().apply {
            mode = "cron"
            fixedDelay = Duration.ofMinutes(30)
            initialDelay = Duration.ofMinutes(1)
            cron = "0 */30 * * * *"
        }
    }

    class Scheduler {
        var enabled: Boolean = true
    }

    class Schedule {
        var mode: String = "fixed-delay"
        var fixedDelay: Duration = Duration.ofSeconds(5)
        var initialDelay: Duration = Duration.ofSeconds(1)
        var cron: String = "*/5 * * * * *"
        var zone: String = ""
    }

    companion object {
        val SCHEDULE_MODES = setOf("fixed-delay", "cron")
    }
}
