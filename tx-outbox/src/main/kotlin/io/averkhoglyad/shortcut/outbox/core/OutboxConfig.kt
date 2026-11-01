package io.averkhoglyad.shortcut.outbox.core

import java.time.Duration

data class OutboxRelayConfig(
    val batchSize: Int,
    val maxLoopsPerRun: Int,
)

data class OutboxLeaseConfig(
    val owner: String,
    val duration: Duration,
)

data class OutboxRetryPolicy(
    val initialDelay: Duration,
    val multiplier: Double,
    val maxDelay: Duration,
    val maxAttempts: Int,
)

data class OutboxCleanerConfig(
    val publishedRetention: Duration,
    val deadRetention: Duration,
)
