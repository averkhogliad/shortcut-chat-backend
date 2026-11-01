package io.averkhoglyad.shortcut.chat.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.chat.core.persistence.entity.OutboxMessageEntity
import io.averkhoglyad.shortcut.chat.core.persistence.repository.OutboxMessageRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class OutboxServiceImplTest : FreeSpec({

    val repository = mockk<OutboxMessageRepository>()
    val objectMapper = mockk<ObjectMapper>()
    val target = OutboxServiceImpl(repository, objectMapper)

    afterTest {
        clearAllMocks()
    }

    "save" - {
        "serializes message and saves it with key" {
            // given
            val body = mapOf("id" to "chat-id")
            val message = OutboxMessage(
                type = "ChatCreated",
                version = "v1",
                key = "chat-id",
                body = body,
            )
            val serialized = byteArrayOf(1, 2, 3)
            every { objectMapper.writeValueAsBytes(body) } returns serialized
            every { repository.save(any()) } answers { args[0] as OutboxMessageEntity }

            // when
            target.save(message)

            // then
            val entity = slot<OutboxMessageEntity>()
            verify(exactly = 1) { repository.save(capture(entity)) }
            entity.captured.type shouldBe message.type
            entity.captured.version shouldBe message.version
            entity.captured.key shouldBe message.key
            entity.captured.body shouldBeSameInstanceAs serialized
        }
    }
})
