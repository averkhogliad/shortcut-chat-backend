package io.averkhoglyad.shortcut.chat.kafka

import io.averkhoglyad.shortcut.chat.core.model.UserCreatedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserDeletedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserUpdatedPayload
import io.averkhoglyad.shortcut.common.kafka.EventNameTypeResolver
import org.springframework.stereotype.Component

private const val EVENT_NAME = "X-Event-Name"

@Component
class UserLifecycleTypeResolver : EventNameTypeResolver(
    EVENT_NAME,
    mapOf(
        "UserCreated" to UserCreatedPayload::class.java,
        "UserUpdated" to UserUpdatedPayload::class.java,
        "UserDeleted" to UserDeletedPayload::class.java,
    ),
)
