package io.averkhoglyad.shortcut.message.core.persistence.repository.util

import io.averkhoglyad.shortcut.message.core.persistence.entity.ChatEntity
import io.averkhoglyad.shortcut.message.core.persistence.entity.MemberEntity
import io.averkhoglyad.shortcut.message.core.persistence.entity.MessageEntity
import io.averkhoglyad.shortcut.message.core.persistence.entity.UserEntity
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.Gen
import io.kotest.property.arbitrary.*
import io.kotest.property.exhaustive.of
import org.springframework.data.jdbc.core.mapping.AggregateReference
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

fun Arb.Companion.userEntities(
    ids: Gen<UUID?> = Exhaustive.of(null),
    externalIds: Gen<String> = Arb.uuid().map { it.toString() }
): Arb<UserEntity> = Arb.bind(
    ids,
    externalIds,
    Arb.string(),
    Arb.email(),
    Arb.boolean(),
) { id, externalId, name, email, deleted, ->
    UserEntity()
        .apply {
            this.id = id
            this.externalId = externalId
            this.name = name
            this.email = email
            this.deleted = deleted
            this.lastSyncAt = Instant.now()
        }
}

/**
 * `chats.last_sync_at` is declared as `TIMESTAMP` without time zone, so an [Instant] passes
 * through the JVM default zone on write and on read. A local time that falls into a DST
 * fall-back overlap is ambiguous: the read side resolves it with a different offset and the
 * value comes back shifted by the overlap duration. Only instants that survive the round trip
 * unchanged are generated, which keeps the property stable in any runner zone. The range is
 * bounded to recent timestamps like [userEntities] does.
 */
private fun Arb.Companion.timestampRoundTripInstants(
    range: ClosedRange<Instant> = Instant.parse("2020-01-01T00:00:00Z")..Instant.now(),
): Arb<Instant> = Arb.javaInstant(range).filter { it.roundTripsThroughSystemZone() }

private fun Instant.roundTripsThroughSystemZone(): Boolean {
    val zone = ZoneId.systemDefault()
    return LocalDateTime.ofInstant(this, zone).atZone(zone).toInstant() == this
}

fun Arb.Companion.chatEntities(
    ids: Gen<UUID?> = Exhaustive.of(null),
    members: Gen<Set<MemberEntity>> = Arb.set(Arb.memberEntities(), 2..5),
    lastSyncAt: Gen<Instant> = Arb.timestampRoundTripInstants(),
): Arb<ChatEntity> = Arb.bind(
    ids,
    Arb.string(),
    members,
    Arb.boolean(),
    lastSyncAt,
) { id, name, memberEntities, deleted, syncAt ->
    ChatEntity()
        .apply {
            this.id = id
            this.name = name
            this.members = memberEntities
            this.deleted = deleted
            this.lastSyncAt = syncAt
        }
}

fun Arb.Companion.memberEntities(
    userIds: Gen<UUID> = Arb.uuid()
): Arb<MemberEntity> = Arb.bind(
    userIds,
    Arb.javaInstant(Instant.EPOCH..Instant.now()),
) { userId, createdAt ->
    MemberEntity()
        .apply {
            this.user = AggregateReference.to(userId)
            this.createdAt = createdAt
        }
}

fun Arb<Set<UUID>>.asMemberEntities() = this.map { userIds ->
    userIds
        .map { Arb.memberEntities(userIds = Exhaustive.of(it)).next() }
        .toSet()
}

fun Arb.Companion.messageEntities(
    ids: Gen<UUID?> = Exhaustive.of(null),
    authorIds: Gen<UUID> = Arb.uuid(),
    chatIds: Gen<UUID> = Arb.uuid(),
): Arb<MessageEntity> = Arb.bind(
    ids,
    Arb.string(minSize = 10, maxSize = 100, codepoints = Codepoint.alphanumeric()),
    authorIds,
    chatIds,
) { id, text, authorId, chatId ->
    MessageEntity()
        .apply {
            this.id = id
            this.text = text
            this.author = AggregateReference.to(authorId)
            this.chat = AggregateReference.to(chatId)
        }
}
