package io.averkhoglyad.shortcut.message.core.model

import java.util.*

data class UserCreatedPayload(
    val id: UUID,
    val name: String,
    val email: String
)

data class UserUpdatedPayload(
    val id: UUID,
    val name: String,
    val email: String
)

data class UserDeletedPayload(
    val id: UUID
)
