package io.averkhoglyad.shortcut.notification.kafka

import io.averkhoglyad.shortcut.common.kafka.EventNameTypeResolver
import io.averkhoglyad.shortcut.common.kafka.KafkaHeaders
import io.averkhoglyad.shortcut.notification.data.ChatCreated
import io.averkhoglyad.shortcut.notification.data.MessagePublished
import org.springframework.stereotype.Component

@Component
class ChatLifecycleTypeResolver : EventNameTypeResolver(
    KafkaHeaders.EVENT_NAME,
    mapOf(
        "ChatCreated" to ChatCreated::class.java,
        "MessagePublished" to MessagePublished::class.java,
    ),
)
