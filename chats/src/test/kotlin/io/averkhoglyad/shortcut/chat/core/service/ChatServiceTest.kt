package io.averkhoglyad.shortcut.chat.core.service

import io.averkhoglyad.shortcut.chat.core.converter.ChatConverter
import io.averkhoglyad.shortcut.chat.core.model.ChatCreatedEvent
import io.averkhoglyad.shortcut.chat.core.model.ChatDetails
import io.averkhoglyad.shortcut.chat.core.model.ChatListItem
import io.averkhoglyad.shortcut.chat.core.model.ChatRequest
import io.averkhoglyad.shortcut.chat.core.model.User
import io.averkhoglyad.shortcut.chat.core.model.UserRef
import io.averkhoglyad.shortcut.chat.core.persistence.entity.ChatEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.MemberEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.UserEntity
import io.averkhoglyad.shortcut.chat.core.persistence.repository.ChatRepository
import io.averkhoglyad.shortcut.chat.core.persistence.repository.UserRepository
import io.averkhoglyad.shortcut.chat.core.service.message.MessageFactory
import io.averkhoglyad.shortcut.chat.core.service.message.OutboxMessage
import io.averkhoglyad.shortcut.common.data.EntityResult.Invalid
import io.averkhoglyad.shortcut.common.data.EntityResult.NotFound
import io.averkhoglyad.shortcut.common.data.EntityResult.Success
import io.averkhoglyad.shortcut.outbox.autoconfigure.OutboxEmitter
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.jdbc.core.mapping.AggregateReference
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class ChatServiceTest : FreeSpec({

    val converter = mockk<ChatConverter>()
    val repository = mockk<ChatRepository>()
    val outboxEmitter = mockk<OutboxEmitter>()
    val messageFactory = mockk<MessageFactory<ChatDetails, ChatCreatedEvent>>()
    val userRepository = mockk<UserRepository>()
    val target = ChatServiceImpl(
        converter = converter,
        repository = repository,
        outboxEmitter = outboxEmitter,
        chatCreatedMessageFactory = messageFactory,
        userRepository = userRepository,
    )

    afterTest {
        clearAllMocks()
    }

    "listForUser" - {
        "returns first page converted to list items with owner" {
            // given
            val userId = randomUUID()
            val ownerId = randomUUID()
            val owner = userEntity(ownerId)
            val entity = chatEntity(ownerId = ownerId)
            val item = entity.toListItem()
            every { repository.findByMembersContainsOrderById(userId, any()) } returns listOf(entity)
            every { userRepository.findByIdIsIn(listOf(ownerId)) } returns listOf(owner)
            every { converter.toListItem(entity, listOf(owner)) } returns item

            // when
            val result = target.listForUser(userId, 10)

            // then
            result shouldBe listOf(item)
        }

        "returns next page converted to list items with owner" {
            // given
            val userId = randomUUID()
            val afterId = randomUUID()
            val ownerId = randomUUID()
            val owner = userEntity(ownerId)
            val entity = chatEntity(ownerId = ownerId)
            val item = entity.toListItem()
            every {
                repository.findByMembersContainsAndIdGreaterThanOrderById(userId, afterId, any())
            } returns listOf(entity)
            every { userRepository.findByIdIsIn(listOf(ownerId)) } returns listOf(owner)
            every { converter.toListItem(entity, listOf(owner)) } returns item

            // when
            val result = target.listForUser(userId, afterId, 10)

            // then
            result shouldBe listOf(item)
        }
    }

    "findById" - {
        "returns NotFound and does not load members when chat is missing" {
            // given
            val chatId = randomUUID()
            every { repository.findById(chatId) } returns null

            // when
            val result = target.findById(chatId)

            // then
            result shouldBe NotFound
            verify(exactly = 0) { userRepository.findByIdIsIn(any()) }
        }

        "returns chat details with owner and members" {
            // given
            val chatId = randomUUID()
            val ownerId = randomUUID()
            val entity = chatEntity(id = chatId, ownerId = ownerId)
            val memberEntities = listOf(userEntity(ownerId), userEntity())
            val details = ChatDetails(
                id = chatId,
                name = entity.name,
                owner = User(ownerId),
                members = emptyList(),
                createdAt = entity.createdAt,
            )
            every { repository.findById(chatId) } returns entity
            every { userRepository.findByIdIsIn(any()) } returns memberEntities
            every { converter.toDetails(entity, memberEntities) } returns details

            // when
            val result = target.findById(chatId)

            // then
            result.shouldBeInstanceOf<Success<ChatDetails>>()
            result.entity shouldBeSameInstanceAs details
        }
    }

    "create" - {
        "saves chat and emits exactly one outbox message when all members are active" {
            // given
            val ownerId = randomUUID()
            val memberIds = listOf(randomUUID(), randomUUID())
            val request = ChatRequest(
                name = "chat",
                members = memberIds.map { UserRef(it) },
            )
            val entity = chatEntity(ownerId = ownerId)
            val saved = chatEntity(ownerId = ownerId)
            val memberEntities = memberIds.map { userEntity(it) }
            val details = chatDetails(saved)
            val outboxMessage = chatCreatedMessage(details)
            every { converter.toEntity(request, ownerId) } returns entity
            every { userRepository.findByIdIsInAndDeletedFalse(any()) } returns memberEntities
            every { repository.save(entity) } returns saved
            every { userRepository.findByIdIsIn(any()) } returns memberEntities
            every { converter.toDetails(saved, memberEntities) } returns details
            every { messageFactory.create(details) } returns outboxMessage
            every { outboxEmitter.emit(any(), any(), any(), any()) } just Runs

            // when
            val result = target.create(request, ownerId)

            // then
            result.shouldBeInstanceOf<Success<ChatDetails>>()
            result.entity shouldBeSameInstanceAs details

            val savedEntity = slot<ChatEntity>()
            verify(exactly = 1) { repository.save(capture(savedEntity)) }
            savedEntity.captured shouldBeSameInstanceAs entity
            savedEntity.captured.owner?.id shouldBe ownerId

            verifyChatCreatedEmittedOnce(outboxEmitter, outboxMessage)
        }

        "returns Invalid and writes nothing when a member is deleted" {
            // given
            val ownerId = randomUUID()
            val activeMemberId = randomUUID()
            val deletedMemberId = randomUUID()
            val request = ChatRequest(
                name = "chat",
                members = listOf(UserRef(activeMemberId), UserRef(deletedMemberId)),
            )
            every { userRepository.findByIdIsInAndDeletedFalse(any()) } returns listOf(userEntity(activeMemberId))
            every { userRepository.findByIdIsIn(any()) } returns listOf(
                userEntity(activeMemberId),
                userEntity(deletedMemberId, deleted = true),
            )

            // when
            val result = target.create(request, ownerId)

            // then
            result.shouldBeInstanceOf<Invalid<ChatDetails>>()
            verify(exactly = 0) { repository.save(any()) }
            verify(exactly = 0) { outboxEmitter.emit(any(), any(), any(), any()) }
        }

        "saves chat when a member id is absent from the projection" {
            // given
            val ownerId = randomUUID()
            val knownMemberId = randomUUID()
            val unknownMemberId = randomUUID()
            val request = ChatRequest(
                name = "chat",
                members = listOf(UserRef(knownMemberId), UserRef(unknownMemberId)),
            )
            val entity = chatEntity(ownerId = ownerId)
            val saved = chatEntity(ownerId = ownerId)
            val memberEntities = listOf(userEntity(knownMemberId))
            val details = chatDetails(saved)
            val outboxMessage = chatCreatedMessage(details)
            every { converter.toEntity(request, ownerId) } returns entity
            every { userRepository.findByIdIsInAndDeletedFalse(any()) } returns memberEntities
            every { userRepository.findByIdIsIn(any()) } returns memberEntities
            every { repository.save(entity) } returns saved
            every { converter.toDetails(saved, memberEntities) } returns details
            every { messageFactory.create(details) } returns outboxMessage
            every { outboxEmitter.emit(any(), any(), any(), any()) } just Runs

            // when
            val result = target.create(request, ownerId)

            // then
            result.shouldBeInstanceOf<Success<ChatDetails>>()
            result.entity shouldBeSameInstanceAs details

            val savedEntity = slot<ChatEntity>()
            verify(exactly = 1) { repository.save(capture(savedEntity)) }
            savedEntity.captured shouldBeSameInstanceAs entity

            verifyChatCreatedEmittedOnce(outboxEmitter, outboxMessage)
        }

        "saves chat and emits exactly one outbox message when the member list is empty" {
            // given
            val ownerId = randomUUID()
            val request = ChatRequest(name = "chat", members = emptyList())
            val entity = chatEntity(ownerId = ownerId)
            val saved = chatEntity(ownerId = ownerId)
            val details = chatDetails(saved)
            val outboxMessage = chatCreatedMessage(details)
            every { converter.toEntity(request, ownerId) } returns entity
            every { userRepository.findByIdIsIn(any()) } returns emptyList<UserEntity>()
            every { repository.save(entity) } returns saved
            every { converter.toDetails(saved, emptyList()) } returns details
            every { messageFactory.create(details) } returns outboxMessage
            every { outboxEmitter.emit(any(), any(), any(), any()) } just Runs

            // when
            val result = target.create(request, ownerId)

            // then
            result.shouldBeInstanceOf<Success<ChatDetails>>()
            result.entity shouldBeSameInstanceAs details

            val savedEntity = slot<ChatEntity>()
            verify(exactly = 1) { repository.save(capture(savedEntity)) }
            savedEntity.captured shouldBeSameInstanceAs entity

            verifyChatCreatedEmittedOnce(outboxEmitter, outboxMessage)
        }
    }

    "findMembersByIds" - {
        "returns empty list without querying repository when no chat ids passed" {
            // when
            val result = target.findMembersByIds(emptyList(), includeDeleted = false)

            // then
            result shouldBe emptyList()
            verify(exactly = 0) { repository.findMembersByChatIdIsIn(any(), any()) }
        }

        "resolves members for all chats with a single query and excludes deleted by default" {
            // given
            val firstChatId = randomUUID()
            val secondChatId = randomUUID()
            val firstUserId = randomUUID()
            val secondUserId = randomUUID()
            every {
                repository.findMembersByChatIdIsIn(listOf(firstChatId, secondChatId), false)
            } returns listOf(
                memberEntity(firstChatId, firstUserId),
                memberEntity(secondChatId, secondUserId),
            )

            // when
            val result = target.findMembersByIds(listOf(firstChatId, secondChatId), includeDeleted = false)

            // then
            result.associate { members -> members.id to members.members.map { it.id } } shouldBe mapOf(
                firstChatId to listOf(firstUserId),
                secondChatId to listOf(secondUserId),
            )

            val queriedChatIds = slot<Collection<UUID>>()
            val queriedIncludeDeleted = slot<Boolean>()
            verify(exactly = 1) {
                repository.findMembersByChatIdIsIn(capture(queriedChatIds), capture(queriedIncludeDeleted))
            }
            queriedChatIds.captured shouldBe listOf(firstChatId, secondChatId)
            queriedIncludeDeleted.captured shouldBe false
        }

        "resolves members for all chats with a single query and keeps deleted when includeDeleted is true" {
            // given
            val firstChatId = randomUUID()
            val secondChatId = randomUUID()
            val activeUserId = randomUUID()
            val deletedUserId = randomUUID()
            every {
                repository.findMembersByChatIdIsIn(listOf(firstChatId, secondChatId), true)
            } returns listOf(
                memberEntity(firstChatId, activeUserId),
                memberEntity(firstChatId, deletedUserId),
                memberEntity(secondChatId, activeUserId),
            )

            // when
            val result = target.findMembersByIds(listOf(firstChatId, secondChatId), includeDeleted = true)

            // then
            result.associate { members -> members.id to members.members.map { it.id } } shouldBe mapOf(
                firstChatId to listOf(activeUserId, deletedUserId),
                secondChatId to listOf(activeUserId),
            )

            val queriedChatIds = slot<Collection<UUID>>()
            val queriedIncludeDeleted = slot<Boolean>()
            verify(exactly = 1) {
                repository.findMembersByChatIdIsIn(capture(queriedChatIds), capture(queriedIncludeDeleted))
            }
            queriedChatIds.captured shouldBe listOf(firstChatId, secondChatId)
            queriedIncludeDeleted.captured shouldBe true
        }

        "returns empty list after a single query when chat id is unknown" {
            // given
            val unknownChatId = randomUUID()
            every {
                repository.findMembersByChatIdIsIn(listOf(unknownChatId), false)
            } returns emptyList()

            // when
            val result = target.findMembersByIds(listOf(unknownChatId), includeDeleted = false)

            // then
            result shouldBe emptyList()

            val queriedChatIds = slot<Collection<UUID>>()
            verify(exactly = 1) {
                repository.findMembersByChatIdIsIn(capture(queriedChatIds), false)
            }
            queriedChatIds.captured shouldBe listOf(unknownChatId)
        }
    }
})

private fun chatEntity(
    id: UUID = randomUUID(),
    name: String = "chat",
    ownerId: UUID? = null,
    members: Set<MemberEntity> = emptySet(),
): ChatEntity = ChatEntity().apply {
    this.id = id
    this.name = name
    this.owner = ownerId?.let { AggregateReference.to<UserEntity, UUID>(it) }
    this.members = members
    this.createdAt = Instant.parse("2026-01-01T00:00:00Z")
}

private fun userEntity(id: UUID = randomUUID(), deleted: Boolean = false): UserEntity = UserEntity().apply {
    this.id = id
    this.name = "user"
    this.email = "$id@example.com"
    this.deleted = deleted
}

private fun chatDetails(entity: ChatEntity) = ChatDetails(
    id = requireNotNull(entity.id),
    name = entity.name,
    owner = entity.owner?.id?.let { User(it) },
    members = emptyList(),
    createdAt = entity.createdAt,
)

private fun chatCreatedMessage(details: ChatDetails) = OutboxMessage<ChatCreatedEvent>(
    type = "ChatCreated",
    version = "v1",
    key = details.id.toString(),
    body = ChatCreatedEvent(
        id = details.id,
        name = details.name,
        owner = details.owner?.let { UserRef(it.id) },
        members = emptyList(),
        createdAt = details.createdAt,
    ),
)

private fun verifyChatCreatedEmittedOnce(
    emitter: OutboxEmitter,
    expected: OutboxMessage<ChatCreatedEvent>,
) {
    val type = slot<String>()
    val version = slot<String>()
    val key = slot<String>()
    val body = slot<Any>()
    verify(exactly = 1) { emitter.emit(capture(type), capture(version), capture(key), capture(body)) }
    type.captured shouldBe expected.type
    version.captured shouldBe expected.version
    key.captured shouldBe expected.key
    body.captured shouldBeSameInstanceAs expected.body
}

private fun memberEntity(chatId: UUID, userId: UUID): MemberEntity = MemberEntity().apply {
    this.chat = AggregateReference.to<ChatEntity, UUID>(chatId)
    this.user = AggregateReference.to<UserEntity, UUID>(userId)
    this.createdAt = Instant.parse("2026-01-01T00:00:00Z")
}

private fun ChatEntity.toListItem() = ChatListItem(
    id = requireNotNull(id),
    name = name,
    owner = owner?.id?.let { User(it) },
    createdAt = createdAt,
)
