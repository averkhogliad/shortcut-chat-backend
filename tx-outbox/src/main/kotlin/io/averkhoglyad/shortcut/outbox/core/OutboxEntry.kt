package io.averkhoglyad.shortcut.outbox.core

import java.time.Instant
import java.util.UUID

data class OutboxEntry(
    val topic: String,
    val key: String?,
    val body: String,
    val headers: Map<String, String>,
    val id: UUID? = null,
    val createdAt: Instant? = null,
)
