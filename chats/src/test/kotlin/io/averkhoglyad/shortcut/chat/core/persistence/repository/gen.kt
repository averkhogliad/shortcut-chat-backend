package io.averkhoglyad.shortcut.chat.core.persistence.repository

import io.averkhoglyad.shortcut.chat.core.persistence.entity.ChatEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.MemberEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.UserEntity
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.Gen
import io.kotest.property.arbitrary.*
import io.kotest.property.exhaustive.of
import org.springframework.data.jdbc.core.mapping.AggregateReference
import java.time.Instant
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
) { id, externalId, name, email, deleted ->
    UserEntity().apply {
        this.id = id
        this.externalId = externalId
        this.name = name
        this.email = email
        this.deleted = deleted
        this.lastSyncAt = Instant.now()
    }
}

fun Arb.Companion.chatEntities(
    ids: Gen<UUID?> = Exhaustive.of(null),
    ownerIds: Gen<UUID?> = Arb.uuid(),
    members: Gen<Set<MemberEntity>> = Arb.set(Arb.memberEntities(), 2..5)
): Arb<ChatEntity> = Arb.bind(
    ids,
    Arb.string(),
    ownerIds,
    members,
    Arb.instant(Instant.EPOCH..Instant.now()),
) { id, name, ownerId, memberEntities, createdAt ->
    ChatEntity().apply {
        this.id = id
        this.name = name
        this.owner = ownerId?.let { AggregateReference.to(ownerId) }
        this.members = memberEntities
        this.createdAt = createdAt
    }
}

fun Arb.Companion.memberEntities(
    userIds: Gen<UUID> = Arb.uuid()
): Arb<MemberEntity> = Arb.bind(
    userIds,
    Arb.instant(Instant.EPOCH..Instant.now()),
) { userId, createdAt ->
    MemberEntity().apply {
        this.user = AggregateReference.to(userId)
        this.createdAt = createdAt
    }
}

fun Arb<Set<UUID>>.asMemberEntities() = this.map { userIds ->
    userIds
        .map { Arb.memberEntities(userIds = Exhaustive.of(it)).next() }
        .toSet()
}
