package io.averkhoglyad.shortcut.outbox.core

import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.Gen
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.exhaustive.of
import java.time.Instant
import java.util.UUID

class OutboxEntryGen(
    val ids: Gen<UUID?> = Exhaustive.of(null),
    val createdAts: Gen<Instant?> = Exhaustive.of(null),
    val keys: Gen<String?> = Exhaustive.of(null, "key-1", "key-2"),
)

fun Arb.Companion.outboxEntries(fieldGens: OutboxEntryGen = OutboxEntryGen()): Arb<OutboxEntry> = Arb.bind(
    Arb.bind(fieldGens.ids, fieldGens.keys, Arb.string(1..20)) { id, key, topic -> Triple(id, key, topic) },
    Arb.uuid().map { """{"id":"$it"}""" },
    headerFields(),
    fieldGens.createdAts,
) { (id, key, topic), body, headers, createdAt ->
    OutboxEntry(
        topic = topic,
        key = key,
        body = body,
        headers = headers,
        id = id,
        createdAt = createdAt,
    )
}

private fun headerFields(): Arb<Map<String, String>> {
    val entry = Arb.bind(Arb.string(1..10), Arb.string(1..20)) { name, value -> name to value }
    return Arb.set(entry, 0..3).map { it.toMap() }
}

fun outboxEntry(
    id: UUID? = null,
    createdAt: Instant? = null,
): OutboxEntry = Arb.outboxEntries(
    OutboxEntryGen(
        ids = Exhaustive.of(id),
        createdAts = Exhaustive.of(createdAt),
    )
).next()
