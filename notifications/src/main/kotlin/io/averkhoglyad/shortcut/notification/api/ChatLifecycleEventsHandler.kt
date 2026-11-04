package io.averkhoglyad.shortcut.notification.api

import io.averkhoglyad.shortcut.common.kafka.KafkaHeaders
import io.averkhoglyad.shortcut.common.util.slf4j
import io.averkhoglyad.shortcut.notification.config.KAFKA_LISTENER_CHAT_LIFECYCLE
import io.averkhoglyad.shortcut.notification.data.ChatLifecycleEvent
import io.averkhoglyad.shortcut.notification.service.NotificationService
import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
@KafkaListener(
    topics = ["ChatLifecycle"],
    idIsGroup = false,
    containerFactory = KAFKA_LISTENER_CHAT_LIFECYCLE,
)
class ChatLifecycleEventsHandler(
    private val service: NotificationService,
) {

    private val log by slf4j()

    @KafkaHandler
    suspend fun handleChatLifecycleEvent(event: ChatLifecycleEvent) {
        service.handleEvent(event)
    }

    @KafkaHandler(isDefault = true)
    suspend fun handleUnknown(
        @Payload payload: Map<String, Any?>,
        @Header(name = KafkaHeaders.EVENT_NAME, required = false, defaultValue = "") eventNameHeader: String
    ) {
        log.warn("Unexpected event `{}` with payload:\n{}", eventNameHeader, payload)
    }
}