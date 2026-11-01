package io.averkhoglyad.shortcut.outbox.core

import io.averkhoglyad.shortcut.outbox.persistence.OutboxDao
import org.springframework.transaction.annotation.Propagation.MANDATORY
import org.springframework.transaction.annotation.Transactional

class OutboxWriterImpl(
    private val dao: OutboxDao,
) : OutboxWriter {

    @Transactional(propagation = MANDATORY)
    override fun write(entry: OutboxEntry) {
        dao.insert(entry)
    }

}
