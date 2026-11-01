package io.averkhoglyad.shortcut.outbox.core

import io.averkhoglyad.shortcut.outbox.persistence.OutboxDao
import java.time.Clock

class OutboxCleanerImpl(
    private val dao: OutboxDao,
    private val config: OutboxCleanerConfig,
    private val clock: Clock = Clock.systemUTC(),
) : OutboxCleaner {

    override fun clean() {
        val now = clock.instant()
        dao.deleteTerminal(
            publishedBefore = now.minus(config.publishedRetention),
            deadBefore = now.minus(config.deadRetention),
        )
    }

}
