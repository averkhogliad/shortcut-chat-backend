package io.averkhoglyad.shortcut.chat.core.service

import io.averkhoglyad.shortcut.chat.core.model.UserCreatedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserDeletedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserUpdatedPayload
import io.averkhoglyad.shortcut.chat.core.persistence.entity.UserEntity
import io.averkhoglyad.shortcut.chat.core.persistence.repository.UserRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import java.time.Instant
import java.util.*

class UserProjectionServiceTest : FreeSpec({

    val repository = mockk<UserRepository>()
    val jdbcAggregateTemplate = mockk<JdbcAggregateTemplate>()
    val service = UserProjectionService(repository, jdbcAggregateTemplate)

    afterTest {
        clearAllMocks()
    }

    "applyCreated" - {
        "inserts a new projection" {
            // given
            val id = UUID.randomUUID()
            val eventTime = Instant.now()
            val payload = UserCreatedPayload(id, "name", "email@e.com")

            val slot = slot<UserEntity>()
            every { repository.findById(id) } returns null
            every { jdbcAggregateTemplate.insert(capture(slot)) } returns UserEntity()

            // when
            service.applyCreated(id, eventTime, payload)

            // then
            slot.captured.id shouldBe id
            slot.captured.externalId shouldBe id.toString()
            slot.captured.name shouldBe "name"
            slot.captured.email shouldBe "email@e.com"
            slot.captured.lastSyncAt shouldBe eventTime
            slot.captured.deleted shouldBe false
        }

        "updates existing projection when event is newer" {
            // given
            val id = UUID.randomUUID()
            val oldTime = Instant.parse("2025-01-01T00:00:00Z")
            val newTime = Instant.parse("2025-02-01T00:00:00Z")
            val payload = UserCreatedPayload(id, "new", "new@e.com")
            val existing = UserEntity().apply {
                this.id = id
                this.externalId = id.toString()
                this.name = "old"
                this.email = "old@e.com"
                this.lastSyncAt = oldTime
                this.deleted = true
            }

            val slot = slot<UserEntity>()
            every { repository.findById(id) } returns existing
            every { repository.save(capture(slot)) } returns existing

            // when
            service.applyCreated(id, newTime, payload)

            // then
            slot.captured.name shouldBe "new"
            slot.captured.email shouldBe "new@e.com"
            slot.captured.lastSyncAt shouldBe newTime
            slot.captured.deleted shouldBe false
        }

        "ignores an older event" {
            // given
            val id = UUID.randomUUID()
            val oldTime = Instant.parse("2025-01-01T00:00:00Z")
            val olderTime = Instant.parse("2024-12-01T00:00:00Z")
            val payload = UserCreatedPayload(id, "new", "new@e.com")
            val existing = UserEntity().apply {
                this.id = id
                this.lastSyncAt = oldTime
            }

            every { repository.findById(id) } returns existing

            // when
            service.applyCreated(id, olderTime, payload)

            // then
            verify(exactly = 0) { repository.save(any()) }
            verify { jdbcAggregateTemplate wasNot Called }
        }

        "ignores an event with equal event time" {
            // given
            val id = UUID.randomUUID()
            val eventTime = Instant.parse("2025-01-01T00:00:00Z")
            val payload = UserCreatedPayload(id, "new", "new@e.com")
            val existing = UserEntity().apply {
                this.id = id
                this.lastSyncAt = eventTime
            }

            every { repository.findById(id) } returns existing

            // when
            service.applyCreated(id, eventTime, payload)

            // then
            verify(exactly = 0) { repository.save(any()) }
            verify { jdbcAggregateTemplate wasNot Called }
        }
    }

    "applyUpdated" - {
        "updates name and email of an existing projection" {
            // given
            val id = UUID.randomUUID()
            val oldTime = Instant.parse("2025-01-01T00:00:00Z")
            val newTime = Instant.parse("2025-02-01T00:00:00Z")
            val payload = UserUpdatedPayload(id, "updated", "updated@e.com")
            val existing = UserEntity().apply {
                this.id = id
                this.externalId = id.toString()
                this.name = "old"
                this.email = "old@e.com"
                this.lastSyncAt = oldTime
                this.deleted = false
            }

            val slot = slot<UserEntity>()
            every { repository.findById(id) } returns existing
            every { repository.save(capture(slot)) } returns existing

            // when
            service.applyUpdated(id, newTime, payload)

            // then
            slot.captured.name shouldBe "updated"
            slot.captured.email shouldBe "updated@e.com"
            slot.captured.lastSyncAt shouldBe newTime
            slot.captured.deleted shouldBe false
        }

        "inserts a new projection when row does not exist" {
            // given
            val id = UUID.randomUUID()
            val eventTime = Instant.now()
            val payload = UserUpdatedPayload(id, "updated", "updated@e.com")

            val slot = slot<UserEntity>()
            every { repository.findById(id) } returns null
            every { jdbcAggregateTemplate.insert(capture(slot)) } returns UserEntity()

            // when
            service.applyUpdated(id, eventTime, payload)

            // then
            slot.captured.id shouldBe id
            slot.captured.name shouldBe "updated"
            slot.captured.email shouldBe "updated@e.com"
            slot.captured.lastSyncAt shouldBe eventTime
        }
    }

    "applyDeleted" - {
        "soft-deletes an existing projection" {
            // given
            val id = UUID.randomUUID()
            val oldTime = Instant.parse("2025-01-01T00:00:00Z")
            val newTime = Instant.parse("2025-02-01T00:00:00Z")
            val payload = UserDeletedPayload(id)
            val existing = UserEntity().apply {
                this.id = id
                this.externalId = id.toString()
                this.name = "name"
                this.email = "email@e.com"
                this.lastSyncAt = oldTime
                this.deleted = false
            }

            val slot = slot<UserEntity>()
            every { repository.findById(id) } returns existing
            every { repository.save(capture(slot)) } returns existing

            // when
            service.applyDeleted(id, newTime, payload)

            // then
            slot.captured.deleted shouldBe true
            slot.captured.lastSyncAt shouldBe newTime
            slot.captured.name shouldBe "name"
        }

        "creates a deleted projection if row does not exist" {
            // given
            val id = UUID.randomUUID()
            val eventTime = Instant.now()
            val payload = UserDeletedPayload(id)

            val slot = slot<UserEntity>()
            every { repository.findById(id) } returns null
            every { jdbcAggregateTemplate.insert(capture(slot)) } returns UserEntity()

            // when
            service.applyDeleted(id, eventTime, payload)

            // then
            slot.captured.id shouldBe id
            slot.captured.deleted shouldBe true
            slot.captured.lastSyncAt shouldBe eventTime
        }
    }
}) {
    init {
        failfast
    }
}
