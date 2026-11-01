package io.averkhoglyad.shortcut.outbox.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.common.util.slf4j
import io.averkhoglyad.shortcut.outbox.messaging.OutboxPublisher
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.jdbc.core.simple.JdbcClient

class OutboxActivationReporter(
    private val jdbcClient: ObjectProvider<JdbcClient>,
    private val objectMapper: ObjectProvider<ObjectMapper>,
    private val publisher: ObjectProvider<OutboxPublisher>,
) : ApplicationListener<ApplicationReadyEvent> {

    private val logger by slf4j()

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        val missing = buildList {
            if (jdbcClient.getIfAvailable() == null) add("JdbcClient")
            if (objectMapper.getIfAvailable() == null) add("ObjectMapper")
            if (publisher.getIfAvailable() == null) add("OutboxPublisher")
        }
        if (missing.isNotEmpty()) {
            logger.warn(
                "tx-outbox is enabled but the outbox runtime is inactive: missing beans {}",
                missing.joinToString(", "),
            )
        }
    }
}
