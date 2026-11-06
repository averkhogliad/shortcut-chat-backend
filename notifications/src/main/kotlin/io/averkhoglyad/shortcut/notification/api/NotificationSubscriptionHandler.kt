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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

@Component
class NotificationSubscriptionHandler(
    private val service: NotificationService
) {

    private val log by slf4j()

    fun subscribe(req: ServerRequest): Mono<ServerResponse> =
        currentUserId()
            .flatMap(::subscribeTo)
            .switchIfEmpty(ServerResponse.status(HttpStatus.UNAUTHORIZED).build())

    private fun subscribeTo(userId: UUID): Mono<ServerResponse> {
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

    private fun currentUserId(): Mono<UUID> =
        ReactiveSecurityContextHolder.getContext()
            .flatMap { context ->
                when (val authentication = context.authentication) {
                    is JwtAuthenticationToken -> Mono.justOrEmpty(authentication.token.subject)
                    else -> Mono.empty<String>()
                }
            }
            .flatMap { subject ->
                Mono.fromCallable { UUID.fromString(subject) }
                    .onErrorResume(IllegalArgumentException::class.java) { Mono.empty<UUID>() }
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
