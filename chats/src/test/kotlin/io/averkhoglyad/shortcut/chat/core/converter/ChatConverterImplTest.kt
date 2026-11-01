package io.averkhoglyad.shortcut.chat.core.converter

import io.averkhoglyad.shortcut.chat.core.model.ChatRequest
import io.averkhoglyad.shortcut.chat.core.model.UserRef
import io.averkhoglyad.shortcut.chat.core.persistence.entity.ChatEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.MemberEntity
import io.averkhoglyad.shortcut.chat.core.persistence.entity.UserEntity
import io.averkhoglyad.shortcut.common.test.betweenInclusive
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.data.jdbc.core.mapping.AggregateReference
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class ChatConverterImplTest : FreeSpec({

    val target = ChatConverterImpl(UserConverterImpl())

    "toDetails" - {
        "maps chat, owner and members" {
            // given
            val chatId = randomUUID()
            val owner = userEntity()
            val member = userEntity()
            val entity = ChatEntity().apply {
                this.id = chatId
                this.name = "chat"
                this.owner = AggregateReference.to<UserEntity, UUID>(requireNotNull(owner.id))
                this.createdAt = Instant.parse("2026-01-01T00:00:00Z")
            }

            // when
            val result = target.toDetails(entity, listOf(owner, member))

            // then
            result.id shouldBe chatId
            result.name shouldBe entity.name
            result.createdAt shouldBe entity.createdAt
            result.owner?.id shouldBe owner.id
            result.members.map { it.id } shouldContainExactlyInAnyOrder listOf(owner.id, member.id)
        }
    }

    "toListItem" - {
        "maps chat list fields" {
            // given
            val entity = ChatEntity().apply {
                this.id = randomUUID()
                this.name = "chat"
                this.createdAt = Instant.parse("2026-01-01T00:00:00Z")
            }

            // when
            val result = target.toListItem(entity)

            // then
            result.id shouldBe entity.id
            result.name shouldBe entity.name
            result.createdAt shouldBe entity.createdAt
        }
    }

    "toEntity" - {
        "creates new chat with requested members" {
            // given
            val firstMemberId = randomUUID()
            val secondMemberId = randomUUID()
            val request = ChatRequest(
                name = "new chat",
                members = listOf(UserRef(firstMemberId), UserRef(secondMemberId)),
            )

            // when
            val result = target.toEntity(request)

            // then
            result.name shouldBe request.name
            result.members.map { it.user.id } shouldContainExactlyInAnyOrder
                listOf(firstMemberId, secondMemberId)
            result.members.forEach { it.createdAt shouldNotBe Instant.MIN }
        }

        "keeps existing members and adds only new ones" {
            // given
            val existingUserId = randomUUID()
            val newUserId = randomUUID()
            val existingCreatedAt = Instant.parse("2025-01-01T00:00:00Z")
            val entity = ChatEntity().apply {
                this.id = randomUUID()
                this.name = "old name"
                this.members = setOf(memberEntity(existingUserId, existingCreatedAt))
            }
            val request = ChatRequest(
                name = "new name",
                members = listOf(UserRef(existingUserId), UserRef(newUserId)),
            )

            // when
            val beforeSave = Instant.now()
            val result = target.toEntity(request, entity)
            val afterSave = Instant.now()

            // then
            result shouldBe entity
            result.name shouldBe request.name
            result.members.map { it.user.id } shouldContainExactlyInAnyOrder
                listOf(existingUserId, newUserId)
            result.members.first { it.user.id == existingUserId }.createdAt shouldBe existingCreatedAt
            result.members.first { it.user.id == newUserId }.createdAt shouldBe
                betweenInclusive(beforeSave, afterSave)
        }
    }
})

private fun userEntity(id: UUID = randomUUID()): UserEntity = UserEntity().apply {
    this.id = id
    this.name = "user"
    this.email = "$id@example.com"
}

private fun memberEntity(userId: UUID, createdAt: Instant): MemberEntity = MemberEntity().apply {
    this.user = AggregateReference.to<UserEntity, UUID>(userId)
    this.createdAt = createdAt
}
