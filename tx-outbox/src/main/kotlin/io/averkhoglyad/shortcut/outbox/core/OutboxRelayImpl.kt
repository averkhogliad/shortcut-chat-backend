package io.averkhoglyad.shortcut.outbox.core

import io.averkhoglyad.shortcut.outbox.messaging.OutboxPublisher
import io.averkhoglyad.shortcut.outbox.messaging.PublishResult
import io.averkhoglyad.shortcut.outbox.persistence.ClaimedOutboxEntry
import io.averkhoglyad.shortcut.outbox.persistence.OutboxDao
import java.time.Clock
import java.time.Duration
import java.util.UUID

class OutboxRelayImpl(
    private val dao: OutboxDao,
    private val publisher: OutboxPublisher,
    private val relayConfig: OutboxRelayConfig,
    private val leaseConfig: OutboxLeaseConfig,
    private val retryPolicy: OutboxRetryPolicy,
    private val clock: Clock = Clock.systemUTC(),
) : OutboxRelay {

    override fun runCycle() {
        val startedAt = clock.instant()
        dao.recoverExpiredLeases(startedAt)
        dao.releaseDueRetries(startedAt)

        var loops = 0
        while (loops < relayConfig.maxLoopsPerRun) {
            val claimed = dao.claimBatch(
                owner = leaseConfig.owner,
                leaseUntil = startedAt.plus(leaseConfig.duration),
                before = startedAt,
                limit = relayConfig.batchSize,
            )
            if (claimed.isEmpty()) {
                break
            }

            val publishedIds = mutableListOf<UUID>()
            claimed.forEach { entry ->
                when (publisher.publish(entry.entry)) {
                    is PublishResult.Success -> publishedIds += requireNotNull(entry.entry.id)
                    is PublishResult.Failure -> handleFailure(entry)
                }
            }
            if (publishedIds.isNotEmpty()) {
                dao.markPublished(publishedIds)
            }
            loops++
        }
    }

    private fun handleFailure(claimed: ClaimedOutboxEntry) {
        val id = requireNotNull(claimed.entry.id)
        val attempt = claimed.retryCount + 1
        if (attempt >= retryPolicy.maxAttempts) {
            dao.markDead(id)
        } else {
            val nextAttemptAt = clock.instant().plus(backoff(attempt))
            dao.markRetry(id, nextAttemptAt, attempt)
        }
    }

    private fun backoff(attempt: Int): Duration {
        val factor = Math.pow(retryPolicy.multiplier, (attempt - 1).toDouble())
        val delayMillis = (retryPolicy.initialDelay.toMillis() * factor).toLong()
        return Duration.ofMillis(delayMillis.coerceAtMost(retryPolicy.maxDelay.toMillis()))
    }

}
