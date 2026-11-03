package io.averkhoglyad.shortcut.message.core.service

import io.averkhoglyad.shortcut.message.core.model.UserCreatedPayload
import io.averkhoglyad.shortcut.message.core.model.UserDeletedPayload
import io.averkhoglyad.shortcut.message.core.model.UserUpdatedPayload
import io.averkhoglyad.shortcut.message.core.persistence.entity.UserEntity
import io.averkhoglyad.shortcut.message.core.persistence.repository.UserRepository
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class UserProjectionService(
    private val repository: UserRepository,
    private val jdbcAggregateTemplate: JdbcAggregateTemplate,
) {

    @Transactional
    fun applyCreated(id: UUID, eventTime: Instant, payload: UserCreatedPayload) {
        apply(id, eventTime) {
            name = payload.name
            email = payload.email
            deleted = false
        }
    }

    @Transactional
    fun applyUpdated(id: UUID, eventTime: Instant, payload: UserUpdatedPayload) {
        apply(id, eventTime) {
            name = payload.name
            email = payload.email
        }
    }

    @Transactional
    fun applyDeleted(id: UUID, eventTime: Instant, payload: UserDeletedPayload) {
        apply(id, eventTime) {
            deleted = true
        }
    }

    private fun apply(id: UUID, eventTime: Instant, block: UserEntity.() -> Unit) {
        val entity = repository.findById(id)
        if (entity != null) {
            if (eventTime <= entity.lastSyncAt) {
                return
            }
            entity.lastSyncAt = eventTime
            entity.block()
            repository.save(entity)
        } else {
            val newEntity = UserEntity().apply {
                this.id = id
                externalId = id.toString()
                lastSyncAt = eventTime
                block()
            }
            jdbcAggregateTemplate.insert(newEntity)
        }
    }
}
