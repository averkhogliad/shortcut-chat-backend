package io.averkhoglyad.shortcut.message.core.service

import io.averkhoglyad.shortcut.message.core.model.ChatCreatedPayload
import io.averkhoglyad.shortcut.message.core.model.UserRef
import io.kotest.property.Arb
import io.kotest.property.Gen
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import java.time.Instant
import java.util.*

fun Arb.Companion.chatCreatedPayloads(
    ids: Gen<UUID> = Arb.uuid(),
    names: Gen<String> = Arb.string(minSize = 1, maxSize = 100, codepoints = Codepoint.alphanumeric()),
    createdAt: Instant = Instant.parse("2025-01-01T00:00:00Z"),
): Arb<ChatCreatedPayload> = Arb.bind(ids, names) { id, name ->
    ChatCreatedPayload(
        id = id,
        name = name,
        owner = UserRef(id),
        members = listOf(UserRef(id)),
        createdAt = createdAt,
    )
}
