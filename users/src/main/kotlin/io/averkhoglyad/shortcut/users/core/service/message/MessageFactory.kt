package io.averkhoglyad.shortcut.users.core.service.message

interface MessageFactory<S, R : Any> {

    fun create(user: S): OutboxMessage<R>

}