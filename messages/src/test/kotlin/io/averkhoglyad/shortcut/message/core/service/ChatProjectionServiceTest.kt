package io.averkhoglyad.shortcut.message.core.service

import io.averkhoglyad.shortcut.message.core.persistence.entity.ChatEntity
import io.averkhoglyad.shortcut.message.core.persistence.repository.ChatRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import java.time.Instant

class ChatProjectionServiceTest : FreeSpec({

    val repository = mockk<ChatRepository>()
    val jdbcAggregateTemplate = mockk<JdbcAggregateTemplate>()
    val service = ChatProjectionService(repository, jdbcAggregateTemplate)

    afterTest {
        clearAllMocks()
    }

    "applyCreated" - {
        "inserts a new projection without chat members" {
            // given
            val payload = Arb.chatCreatedPayloads().next()
            val id = payload.id
            val eventTime = Instant.parse("2025-06-01T12:00:00Z")
            val slot = slot<ChatEntity>()
            every { repository.findById(id) } returns null
            every { jdbcAggregateTemplate.insert(capture(slot)) } returns ChatEntity()

            // when
            service.applyCreated(id, eventTime, payload)

            // then
            slot.captured.id shouldBe id
            slot.captured.name shouldBe payload.name
            slot.captured.lastSyncAt shouldBe eventTime
            slot.captured.deleted shouldBe false
            slot.captured.members shouldBe emptySet()
        }

        "updates existing projection when event is newer" {
            // given
            val payload = Arb.chatCreatedPayloads().next()
            val id = payload.id
            val oldTime = Instant.parse("2025-01-01T00:00:00Z")
            val newTime = Instant.parse("2025-02-01T00:00:00Z")
            val existing = ChatEntity().apply {
                this.id = id
                this.name = "stored name"
                this.lastSyncAt = oldTime
            }
            val slot = slot<ChatEntity>()
            every { repository.findById(id) } returns existing
            every { repository.save(capture(slot)) } returns existing

            // when
            service.applyCreated(id, newTime, payload)

            // then
            slot.captured.id shouldBe id
            slot.captured.name shouldBe payload.name
            slot.captured.lastSyncAt shouldBe newTime
        }

        "ignores an older event" {
            // given
            val payload = Arb.chatCreatedPayloads().next()
            val id = payload.id
            val storedTime = Instant.parse("2025-01-01T00:00:00Z")
            val olderTime = Instant.parse("2024-12-01T00:00:00Z")
            val existing = ChatEntity().apply {
                this.id = id
                this.name = "stored name"
                this.lastSyncAt = storedTime
            }
            every { repository.findById(id) } returns existing

            // when
            service.applyCreated(id, olderTime, payload)

            // then
            existing.name shouldBe "stored name"
            existing.lastSyncAt shouldBe storedTime
            verify(exactly = 0) { repository.save(any()) }
            verify { jdbcAggregateTemplate wasNot Called }
        }

        "ignores an event with equal event time" {
            // given
            val payload = Arb.chatCreatedPayloads().next()
            val id = payload.id
            val eventTime = Instant.parse("2025-01-01T00:00:00Z")
            val existing = ChatEntity().apply {
                this.id = id
                this.name = "stored name"
                this.lastSyncAt = eventTime
            }
            every { repository.findById(id) } returns existing

            // when
            service.applyCreated(id, eventTime, payload)

            // then
            existing.name shouldBe "stored name"
            existing.lastSyncAt shouldBe eventTime
            verify(exactly = 0) { repository.save(any()) }
            verify { jdbcAggregateTemplate wasNot Called }
        }
    }
}) {
    init {
        failfast
    }
}
