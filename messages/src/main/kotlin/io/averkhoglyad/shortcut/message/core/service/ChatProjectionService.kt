package io.averkhoglyad.shortcut.message.core.service

import io.averkhoglyad.shortcut.message.core.model.ChatCreatedPayload
import io.averkhoglyad.shortcut.message.core.persistence.entity.ChatEntity
import io.averkhoglyad.shortcut.message.core.persistence.repository.ChatRepository
import org.springframework.data.jdbc.core.JdbcAggregateTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class ChatProjectionService(
    private val repository: ChatRepository,
    private val jdbcAggregateTemplate: JdbcAggregateTemplate,
) {

    @Transactional
    fun applyCreated(id: UUID, eventTime: Instant, payload: ChatCreatedPayload) {
        val entity = repository.findById(id)
        if (entity != null) {
            if (eventTime <= entity.lastSyncAt) {
                return
            }
            entity.name = payload.name
            entity.lastSyncAt = eventTime
            repository.save(entity)
        } else {
            val newEntity = ChatEntity().apply {
                this.id = id
                this.name = payload.name
                this.lastSyncAt = eventTime
                this.deleted = false
                this.members = emptySet()
            }
            jdbcAggregateTemplate.insert(newEntity)
        }
    }
}
