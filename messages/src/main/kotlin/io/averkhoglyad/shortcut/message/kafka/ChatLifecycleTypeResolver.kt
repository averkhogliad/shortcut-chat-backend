package io.averkhoglyad.shortcut.message.kafka

import io.averkhoglyad.shortcut.common.kafka.EventNameTypeResolver
import io.averkhoglyad.shortcut.message.core.model.ChatCreatedPayload
import org.springframework.stereotype.Component

private const val EVENT_NAME = "X-Event-Name"

@Component
class ChatLifecycleTypeResolver : EventNameTypeResolver(
    EVENT_NAME,
    mapOf(
        "ChatCreated" to ChatCreatedPayload::class.java,
    ),
)
