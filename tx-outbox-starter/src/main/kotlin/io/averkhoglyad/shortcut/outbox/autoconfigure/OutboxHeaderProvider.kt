package io.averkhoglyad.shortcut.outbox.autoconfigure

import io.averkhoglyad.shortcut.common.kafka.KafkaHeaders
import java.time.Instant
import java.util.UUID

object OutboxHeaderNames {

    const val EVENT_ID = KafkaHeaders.EVENT_ID
    const val EVENT_NAME = KafkaHeaders.EVENT_NAME
    const val EVENT_VERSION = KafkaHeaders.EVENT_VERSION
    const val PUBLISHED_AT = KafkaHeaders.PUBLISHED_AT
    const val PUBLISHED_BY = KafkaHeaders.PUBLISHED_BY

    val STANDARD = setOf(EVENT_ID, EVENT_NAME, EVENT_VERSION, PUBLISHED_AT, PUBLISHED_BY)

}

interface OutboxHeaderProvider {

    fun headersFor(
        type: String,
        version: String,
        route: Route,
        entryId: UUID,
        now: Instant,
        applicationName: String,
    ): Map<String, String>

}

class DefaultOutboxHeaderProvider : OutboxHeaderProvider {

    override fun headersFor(
        type: String,
        version: String,
        route: Route,
        entryId: UUID,
        now: Instant,
        applicationName: String,
    ): Map<String, String> {
        val headers = linkedMapOf(
            OutboxHeaderNames.EVENT_ID to entryId.toString(),
            OutboxHeaderNames.EVENT_NAME to route.eventName,
            OutboxHeaderNames.EVENT_VERSION to version,
            OutboxHeaderNames.PUBLISHED_AT to now.toString(),
            OutboxHeaderNames.PUBLISHED_BY to applicationName,
        )
        route.headers.forEach { (name, value) ->
            if (name !in OutboxHeaderNames.STANDARD) {
                headers[name] = value
            }
        }
        return headers
    }
}
