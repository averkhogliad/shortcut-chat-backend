package io.averkhoglyad.shortcut.message.core.model

import java.time.Instant
import java.util.*

data class ChatCreatedPayload(
    val id: UUID,
    val name: String,
    val owner: UserRef?,
    val members: Collection<UserRef>,
    val createdAt: Instant,
)
