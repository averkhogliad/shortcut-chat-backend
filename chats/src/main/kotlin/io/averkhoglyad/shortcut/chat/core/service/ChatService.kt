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

    fun create(chat: ChatRequest): EntityResult<ChatDetails>

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
            .map { it.toListItem() }
    }

    override fun listForUser(userId: UUID, afterId: UUID, pageSize: Int): List<ChatListItem> {
        return repository.findByMembersContainsAndIdGreaterThanOrderById(userId, afterId, Pageable.ofSize(pageSize))
            .map { it.toListItem() }
    }

    override fun findById(chatId: UUID): EntityResult<ChatDetails> {
        return repository.findById(chatId)
            ?.let { it to userRepository.findByIdIsIn(it.memberIds) }
            ?.let { (entity, members) -> entity.toDetails(members) }
            ?.let { EntityResult.Success(it) }
            ?: EntityResult.NotFound
    }

    @Transactional
    override fun create(chat: ChatRequest): EntityResult<ChatDetails> {
        if (hasDeletedMembers(chat)) {
            return EntityResult.Invalid("members reference deleted users")
        }
        return repository.save(chat.toEntity())
            .let { it to userRepository.findByIdIsIn(it.memberIds) }
            .let { (entity, members) -> entity.toDetails(members) }
            .also { emitChatCreatedLifecycleEvent(it) }
            .let { EntityResult.Success(it) }
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

    private fun ChatRequest.toEntity(): ChatEntity = converter.toEntity(this)

    private fun ChatEntity.toDetails(memberUsers: Collection<UserEntity>): ChatDetails =
        converter.toDetails(this, memberUsers)

    private fun ChatEntity.toListItem(): ChatListItem = converter.toListItem(this)
}

private val ChatEntity.memberIds: Collection<UUID>
    get() = members.mapNotNull { it.user.id }
