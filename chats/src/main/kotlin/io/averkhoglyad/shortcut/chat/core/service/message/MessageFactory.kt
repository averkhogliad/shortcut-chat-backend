package io.averkhoglyad.shortcut.chat.core.service.message

interface MessageFactory<S, R> {

    fun create(body: S): OutboxMessage<R>

}