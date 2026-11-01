package io.averkhoglyad.shortcut.users.test.gen

import io.averkhoglyad.shortcut.users.core.persistence.entity.OutboxMessageEntity
import java.time.Instant
import java.util.UUID

fun outboxMessage(
    id: UUID = UUID.randomUUID(),
    type: String = "UserCreated",
    version: String = "v1",
    body: ByteArray = byteArrayOf(1, 2, 3),
    createdAt: Instant = Instant.now(),
    publishedAt: Instant? = null,
): OutboxMessageEntity = OutboxMessageEntity().apply {
    this.id = id
    this.type = type
    this.version = version
    this.body = body
    this.createdAt = createdAt
    this.publishedAt = publishedAt
}
