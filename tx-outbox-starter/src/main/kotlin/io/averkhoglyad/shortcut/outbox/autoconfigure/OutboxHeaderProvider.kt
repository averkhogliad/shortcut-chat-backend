package io.averkhoglyad.shortcut.outbox.autoconfigure

import java.time.Instant
import java.util.UUID

object OutboxHeaderNames {

    const val EVENT_ID = "X-Event-Id"
    const val EVENT_NAME = "X-Event-Name"
    const val EVENT_VERSION = "X-Event-Version"
    const val PUBLISHED_AT = "X-Published-At"
    const val PUBLISHED_BY = "X-Published-By"

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
