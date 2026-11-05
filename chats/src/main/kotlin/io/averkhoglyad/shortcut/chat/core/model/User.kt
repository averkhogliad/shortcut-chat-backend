package io.averkhoglyad.shortcut.chat.core.model

import java.util.*

data class User(
    val id: UUID,
    val name: String = "",
    val email: String = "",
    val deleted: Boolean = false,
)

data class UserRef(val id: UUID)
