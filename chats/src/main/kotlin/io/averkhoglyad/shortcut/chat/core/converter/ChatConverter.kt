package io.averkhoglyad.shortcut.chat.core.converter

import io.averkhoglyad.shortcut.chat.core.model.ChatDetails
import io.averkhoglyad.shortcut.chat.core.model.ChatListItem
import io.averkhoglyad.shortcut.chat.core.model.ChatRequest
import io.averkhoglyad.shortcut.chat.core.model.UserRef
import io.averkhoglyad.shortcut.chat.core.persistence.entity.ChatEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.MemberEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.UserEntity
import org.springframework.data.jdbc.core.mapping.AggregateReference
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.*

interface ChatConverter {

    fun toEntity(chat: ChatRequest, ownerId: UUID): ChatEntity

    fun toEntity(chat: ChatRequest, entity: ChatEntity): ChatEntity

    fun toDetails(entity: ChatEntity, users: Collection<UserEntity>): ChatDetails

    fun toListItem(entity: ChatEntity, users: Collection<UserEntity>): ChatListItem

}

@Component
class ChatConverterImpl(
    private val userConverter: UserConverter,
) : ChatConverter {

    override fun toEntity(chat: ChatRequest, ownerId: UUID): ChatEntity {
        return toEntity(chat, ChatEntity())
            .apply { this.owner = ownerId.asRef() }
    }

    override fun toEntity(chat: ChatRequest, entity: ChatEntity): ChatEntity {
        return entity
            .apply {
                this.name = chat.name
                this.members = this.members.mergeWith(chat.members).toSet()
            }
    }

    override fun toListItem(entity: ChatEntity, users: Collection<UserEntity>): ChatListItem {
        return ChatListItem(
            id = requireNotNull(entity.id),
            name = entity.name,
            owner = entity.ownerUser(users)?.let(userConverter::toDetails),
            createdAt = entity.createdAt,
        )
    }

    override fun toDetails(entity: ChatEntity, users: Collection<UserEntity>): ChatDetails {
        return ChatDetails(
            id = requireNotNull(entity.id),
            name = entity.name,
            owner = entity.ownerUser(users)?.let(userConverter::toDetails),
            members = entity.members.mapNotNull { member -> users.firstOrNull { it.id == member.user.id } }
                .map(userConverter::toDetails),
            createdAt = entity.createdAt,
        )
    }
}

private fun ChatEntity.ownerUser(users: Collection<UserEntity>): UserEntity? =
    owner?.id?.let { ownerId -> users.firstOrNull { it.id == ownerId } }


private fun Collection<MemberEntity>.mergeWith(other: Collection<UserRef>): Collection<MemberEntity> {
    val result = arrayListOf<MemberEntity>()

    this.filter { el -> other.any { it.id == el.user.id } }
        .toCollection(result)

    other.filterNot { el -> this.any { it.user.id == el.id } }
        .map {
            MemberEntity()
                .apply {
                    this.user = it.id.asRef()
                    this.createdAt = Instant.now()
                }
        }
        .toCollection(result)

    return result
}

private fun UUID.asRef() = AggregateReference.to<UserEntity, UUID>(this)
