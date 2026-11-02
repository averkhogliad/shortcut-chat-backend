package io.averkhoglyad.shortcut.chat.core.service

import io.averkhoglyad.shortcut.chat.core.converter.ChatConverter
import io.averkhoglyad.shortcut.chat.core.model.ChatCreatedEvent
import io.averkhoglyad.shortcut.chat.core.model.ChatDetails
import io.averkhoglyad.shortcut.chat.core.model.ChatListItem
import io.averkhoglyad.shortcut.chat.core.model.ChatRequest
import io.averkhoglyad.shortcut.chat.core.model.UserRef
import io.averkhoglyad.shortcut.chat.core.persistence.entity.ChatEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.MemberEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.UserEntity
import io.averkhoglyad.shortcut.chat.core.persistence.repository.ChatRepository
import io.averkhoglyad.shortcut.chat.core.persistence.repository.UserRepository
import io.averkhoglyad.shortcut.chat.core.service.message.MessageFactory
import io.averkhoglyad.shortcut.chat.core.service.message.OutboxMessage
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
        "returns first page converted to list items" {
            // given
            val userId = randomUUID()
            val entity = chatEntity()
            val item = entity.toListItem()
            every { repository.findByMembersContainsOrderById(userId, any()) } returns listOf(entity)
            every { converter.toListItem(entity) } returns item

            // when
            val result = target.listForUser(userId, 10)

            // then
            result shouldBe listOf(item)
        }

        "returns next page converted to list items" {
            // given
            val userId = randomUUID()
            val afterId = randomUUID()
            val entity = chatEntity()
            val item = entity.toListItem()
            every {
                repository.findByMembersContainsAndIdGreaterThanOrderById(userId, afterId, any())
            } returns listOf(entity)
            every { converter.toListItem(entity) } returns item

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

        "returns chat details with members" {
            // given
            val chatId = randomUUID()
            val entity = chatEntity(id = chatId)
            val memberEntities = listOf(userEntity(), userEntity())
            val details = ChatDetails(
                id = chatId,
                name = entity.name,
                owner = null,
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
        "saves chat and emits exactly one outbox message" {
            // given
            val request = ChatRequest(
                name = "chat",
                members = listOf(UserRef(randomUUID()), UserRef(randomUUID())),
            )
            val entity = chatEntity()
            val saved = chatEntity()
            val memberEntities = listOf(userEntity())
            val details = ChatDetails(
                id = requireNotNull(saved.id),
                name = saved.name,
                owner = null,
                members = emptyList(),
                createdAt = saved.createdAt,
            )
            val outboxMessage = OutboxMessage<ChatCreatedEvent>(
                type = "ChatCreated",
                version = "v1",
                key = details.id.toString(),
                body = ChatCreatedEvent(
                    id = details.id,
                    name = details.name,
                    owner = null,
                    members = emptyList(),
                    createdAt = details.createdAt,
                ),
            )
            every { converter.toEntity(request) } returns entity
            every { repository.save(entity) } returns saved
            every { userRepository.findByIdIsIn(any()) } returns memberEntities
            every { converter.toDetails(saved, memberEntities) } returns details
            every { messageFactory.create(details) } returns outboxMessage
            every { outboxEmitter.emit(any(), any(), any(), any()) } just Runs

            // when
            val result = target.create(request)

            // then
            result.shouldBeInstanceOf<Success<ChatDetails>>()
            result.entity shouldBeSameInstanceAs details

            val type = slot<String>()
            val version = slot<String>()
            val key = slot<String>()
            val body = slot<Any>()
            verify(exactly = 1) {
                outboxEmitter.emit(capture(type), capture(version), capture(key), capture(body))
            }
            type.captured shouldBe outboxMessage.type
            version.captured shouldBe outboxMessage.version
            key.captured shouldBe outboxMessage.key
            body.captured shouldBeSameInstanceAs outboxMessage.body
        }
    }

    "findMembersByIds" - {
        "returns empty list without querying repository when no chat ids passed" {
            // when
            val result = target.findMembersByIds(emptyList())

            // then
            result shouldBe emptyList()
            verify(exactly = 0) { repository.findMembersByChatIdIsIn(any()) }
        }

        "groups members by chat id" {
            // given
            val firstChatId = randomUUID()
            val secondChatId = randomUUID()
            val firstUserId = randomUUID()
            val secondUserId = randomUUID()
            val thirdUserId = randomUUID()
            every { repository.findMembersByChatIdIsIn(listOf(firstChatId, secondChatId)) } returns listOf(
                memberEntity(firstChatId, firstUserId),
                memberEntity(firstChatId, secondUserId),
                memberEntity(secondChatId, thirdUserId),
            )

            // when
            val result = target.findMembersByIds(listOf(firstChatId, secondChatId))

            // then
            result.associate { members -> members.id to members.members.map { it.id } } shouldBe mapOf(
                firstChatId to listOf(firstUserId, secondUserId),
                secondChatId to listOf(thirdUserId),
            )
        }
    }
})

private fun chatEntity(
    id: UUID = randomUUID(),
    name: String = "chat",
    members: Set<MemberEntity> = emptySet(),
): ChatEntity = ChatEntity().apply {
    this.id = id
    this.name = name
    this.members = members
    this.createdAt = Instant.parse("2026-01-01T00:00:00Z")
}

private fun userEntity(id: UUID = randomUUID()): UserEntity = UserEntity().apply {
    this.id = id
    this.name = "user"
    this.email = "$id@example.com"
}

private fun memberEntity(chatId: UUID, userId: UUID): MemberEntity = MemberEntity().apply {
    this.chat = AggregateReference.to<ChatEntity, UUID>(chatId)
    this.user = AggregateReference.to<UserEntity, UUID>(userId)
    this.createdAt = Instant.parse("2026-01-01T00:00:00Z")
}

private fun ChatEntity.toListItem() = ChatListItem(
    id = requireNotNull(id),
    name = name,
    owner = null,
    createdAt = createdAt,
)
