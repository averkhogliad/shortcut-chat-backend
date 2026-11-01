package io.averkhoglyad.shortcut.users.core.service.message

data class OutboxMessage<B : Any>(
    val type: String,
    val version: String,
    val key: String? = null,
    val body: B
)
