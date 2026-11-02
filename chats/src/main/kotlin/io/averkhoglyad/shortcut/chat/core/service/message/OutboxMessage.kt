package io.averkhoglyad.shortcut.chat.core.service.message

data class OutboxMessage<B>(
    val type: String,
    val version: String,
    val key: String? = null,
    val body: B,
)
