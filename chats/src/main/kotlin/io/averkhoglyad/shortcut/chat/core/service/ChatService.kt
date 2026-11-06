package io.averkhoglyad.shortcut.chat.core.service

import io.averkhoglyad.shortcut.chat.core.converter.ChatConverter
import io.averkhoglyad.shortcut.chat.core.model.*
import io.averkhoglyad.shortcut.chat.core.persistence.entity.ChatEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.UserEntity
import io.averkhoglyad.shortcut.chat.core.persistence.repository.ChatRepository
import io.averkhoglyad.shortcut.chat.core.persistence.repository.UserRepository
import io.averkhoglyad.shortcut.chat.core.service.message.MessageFactory
import io.averkhoglyad.shortcut.common.data.EntityResult
import io.averkhoglyad.shortcut.outbox.autoconfigure.OutboxEmitter
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface ChatService {

    fun listForUser(userId: UUID, pageSize: Int): List<ChatListItem>

    fun listForUser(userId: UUID, afterId: UUID, pageSize: Int): List<ChatListItem>

    fun findById(chatId: UUID): EntityResult<ChatDetails>

    fun create(chat: ChatRequest, ownerId: UUID): EntityResult<ChatDetails>

    fun findMembersByIds(chatIds: Collection<UUID>, includeDeleted: Boolean = false): List<ChatMembers>
}

@Service
class ChatServiceImpl(
    private val converter: ChatConverter,
    private val repository: ChatRepository,
    private val outboxEmitter: OutboxEmitter,
    private val chatCreatedMessageFactory: MessageFactory<ChatDetails, ChatCreatedEvent>,
    private val userRepository: UserRepository,
) : ChatService {

    override fun listForUser(userId: UUID, pageSize: Int): List<ChatListItem> {
        return repository.findByMembersContainsOrderById(userId, Pageable.ofSize(pageSize))
            .toListItems()
    }

    override fun listForUser(userId: UUID, afterId: UUID, pageSize: Int): List<ChatListItem> {
        return repository.findByMembersContainsAndIdGreaterThanOrderById(userId, afterId, Pageable.ofSize(pageSize))
            .toListItems()
    }

    override fun findById(chatId: UUID): EntityResult<ChatDetails> {
        val entity = repository.findById(chatId)
            ?: return EntityResult.NotFound
        return EntityResult.Success(entity.toDetails(loadUsers(entity.userIds)))
    }

    @Transactional
    override fun create(chat: ChatRequest, ownerId: UUID): EntityResult<ChatDetails> {
        if (hasDeletedMembers(chat)) {
            return EntityResult.Invalid("members reference deleted users")
        }
        val details = repository.save(chat.toEntity(ownerId))
            .let { it.toDetails(loadUsers(it.userIds)) }
        emitChatCreatedLifecycleEvent(details)
        return EntityResult.Success(details)
    }

    override fun findMembersByIds(chatIds: Collection<UUID>, includeDeleted: Boolean): List<ChatMembers> {
        if (chatIds.isEmpty()) {
            return emptyList()
        }
        return repository.findMembersByChatIdIsIn(chatIds, includeDeleted)
            .groupBy { it.chat.id }
            .map { (chatId, entity) ->
                ChatMembers(
                    id = requireNotNull(chatId),
                    members = entity.map { UserRef(requireNotNull(it.user.id)) }
                )
            }
    }

    private fun emitChatCreatedLifecycleEvent(chat: ChatDetails) {
        chatCreatedMessageFactory.create(chat)
            .let { outboxEmitter.emit(it.type, it.version, it.key, it.body) }
    }

    private fun hasDeletedMembers(chat: ChatRequest): Boolean {
        val memberIds = chat.members.map { it.id }.toSet()
        if (memberIds.isEmpty()) {
            return false
        }
        val activeMemberIds = userRepository.findByIdIsInAndDeletedFalse(memberIds)
            .mapNotNull { it.id }
            .toSet()
        if (activeMemberIds.containsAll(memberIds)) {
            return false
        }
        // ids missing from the non-deleted projection are either deleted or not projected at all
        return userRepository.findByIdIsIn(memberIds)
            .any { it.deleted }
    }

    private fun ChatRequest.toEntity(ownerId: UUID): ChatEntity = converter.toEntity(this, ownerId)

    private fun ChatEntity.toDetails(users: Collection<UserEntity>): ChatDetails =
        converter.toDetails(this, users)

    private fun List<ChatEntity>.toListItems(): List<ChatListItem> =
        ownerUsers().let { users -> map { converter.toListItem(it, users) } }

    private fun List<ChatEntity>.ownerUsers(): Collection<UserEntity> =
        loadUsers(mapNotNull { it.owner?.id }.distinct())

    private fun loadUsers(ids: Collection<UUID>): Collection<UserEntity> =
        if (ids.isEmpty()) emptyList() else userRepository.findByIdIsIn(ids)
}

private val ChatEntity.memberIds: Collection<UUID>
    get() = members.mapNotNull { it.user.id }

private val ChatEntity.userIds: Collection<UUID>
    get() = (memberIds + listOfNotNull(owner?.id)).distinct()
