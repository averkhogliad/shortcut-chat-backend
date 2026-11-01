package io.averkhoglyad.shortcut.chat.outbox

import io.averkhoglyad.shortcut.chat.core.persistence.entity.OutboxMessageEntity
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.instant
import io.kotest.property.arbitrary.int
import java.time.Instant
import java.time.Period
import java.util.UUID

fun outboxMessage(
    id: UUID = UUID.randomUUID(),
    type: String = "ChatCreated",
    key: String? = UUID.randomUUID().toString(),
    version: String = "v1",
    body: ByteArray = byteArrayOf(1, 2, 3),
    createdAt: Instant = Instant.now(),
    publishedAt: Instant? = null,
): OutboxMessageEntity = OutboxMessageEntity().apply {
    this.id = id
    this.type = type
    this.key = key
    this.version = version
    this.body = body
    this.createdAt = createdAt
    this.publishedAt = publishedAt
}

val nowAndPeriodPairs: Arb<Pair<Instant, Period>> = Arb.bind(
    Arb.instant(Instant.EPOCH, Instant.now()),
    Arb.int(10..1000),
) { now, days ->
    now to Period.ofDays(days)
}
