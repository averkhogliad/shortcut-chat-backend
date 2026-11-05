package io.averkhoglyad.shortcut.notification.api

import com.fasterxml.jackson.annotation.JsonValue
import io.averkhoglyad.shortcut.common.util.info
import io.averkhoglyad.shortcut.common.util.slf4j
import io.averkhoglyad.shortcut.notification.data.ChatEventWithMembers
import io.averkhoglyad.shortcut.notification.data.ChatLifecycleEvent
import io.averkhoglyad.shortcut.notification.data.DebugEvent
import io.averkhoglyad.shortcut.notification.service.NotificationService
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.queryParamOrNull
import reactor.core.publisher.Mono
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

@Component
class NotificationSubscriptionHandler(
    private val service: NotificationService
) {

    private val log by slf4j()

    fun subscribe(req: ServerRequest): Mono<ServerResponse> {
        val userId = req.queryParamOrNull("userId")
            ?.let { UUID.fromString(it) }
            ?: throw IllegalArgumentException("User ID header must be passed")

        val events = service.events()
            .map { events -> EventsResponse(events.visibleTo(userId)) }
            .onStart { log.info("started") }
            .onEach { log.info("received {}", it) }
            .onCompletion { err ->
                log.info {
                    err?.let { if (err is CancellationException) "canceled" else "failed: ${err.message}" } ?: "completed"
                }
            }

        return ServerResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(events, EventsResponse::class.java)
    }
}


data class EventsResponse(@JsonValue val events: List<ChatLifecycleEvent>)

/**
 * The audience of a subscription is the member list returned by `chats` (`GET /members`).
 * Deleted users are filtered out there by default, so they are absent here — the subscriber
 * only receives events of chats whose returned member list contains them.
 */
internal fun List<ChatEventWithMembers>.visibleTo(userId: UUID): List<ChatLifecycleEvent> =
    filter { el -> el.event is DebugEvent || el.members.any { it.id == userId } }
        .map { it.event }
        .sortedBy { it.createdAt }
