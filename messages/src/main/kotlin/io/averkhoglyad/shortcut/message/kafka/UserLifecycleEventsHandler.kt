package io.averkhoglyad.shortcut.message.kafka

import io.averkhoglyad.shortcut.message.config.KAFKA_LISTENER_USER_LIFECYCLE
import io.averkhoglyad.shortcut.message.core.model.UserCreatedPayload
import io.averkhoglyad.shortcut.message.core.model.UserDeletedPayload
import io.averkhoglyad.shortcut.message.core.model.UserUpdatedPayload
import io.averkhoglyad.shortcut.message.core.service.UserProjectionService
import io.averkhoglyad.shortcut.common.util.slf4j
import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.time.Instant

private const val PUBLISHED_AT = "X-Published-At"
private const val EVENT_NAME = "X-Event-Name"

@Component
@KafkaListener(
    topics = ["UserLifecycle"],
    idIsGroup = false,
    containerFactory = KAFKA_LISTENER_USER_LIFECYCLE,
)
class UserLifecycleEventsHandler(
    private val service: UserProjectionService,
) {

    private val log by slf4j()

    @KafkaHandler
    fun handleCreated(payload: UserCreatedPayload, @Header(name = PUBLISHED_AT) publishedAt: String) {
        service.applyCreated(payload.id, Instant.parse(publishedAt), payload)
    }

    @KafkaHandler
    fun handleUpdated(payload: UserUpdatedPayload, @Header(name = PUBLISHED_AT) publishedAt: String) {
        service.applyUpdated(payload.id, Instant.parse(publishedAt), payload)
    }

    @KafkaHandler
    fun handleDeleted(payload: UserDeletedPayload, @Header(name = PUBLISHED_AT) publishedAt: String) {
        service.applyDeleted(payload.id, Instant.parse(publishedAt), payload)
    }

    @KafkaHandler(isDefault = true)
    fun handleUnknown(
        @Payload payload: Map<String, Any?>,
        @Header(name = EVENT_NAME, required = false, defaultValue = "") eventNameHeader: String
    ) {
        log.warn("Unexpected event `{}` with payload:\n{}", eventNameHeader, payload)
    }
}
