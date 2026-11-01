package io.averkhoglyad.shortcut.outbox.persistence

import io.averkhoglyad.shortcut.outbox.core.OutboxEntry

data class ClaimedOutboxEntry(
    val entry: OutboxEntry,
    val retryCount: Int,
)
