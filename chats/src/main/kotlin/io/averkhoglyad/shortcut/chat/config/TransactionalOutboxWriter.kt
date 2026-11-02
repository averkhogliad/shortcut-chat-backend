package io.averkhoglyad.shortcut.chat.config

import io.averkhoglyad.shortcut.outbox.core.OutboxEntry
import io.averkhoglyad.shortcut.outbox.core.OutboxWriter
import io.averkhoglyad.shortcut.outbox.persistence.OutboxDao
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

@Component
class TransactionalOutboxWriter(
    private val dao: OutboxDao,
) : OutboxWriter {

    @Transactional(propagation = MANDATORY)
    override fun write(entry: OutboxEntry) {
        dao.insert(entry)
    }
}
