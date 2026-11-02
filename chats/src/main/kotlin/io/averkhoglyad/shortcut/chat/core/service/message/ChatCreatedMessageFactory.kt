package io.averkhoglyad.shortcut.chat.core.service.message

import io.averkhoglyad.shortcut.chat.core.model.ChatCreatedEvent
import io.averkhoglyad.shortcut.chat.core.model.ChatDetails
import io.averkhoglyad.shortcut.chat.core.model.User
import io.averkhoglyad.shortcut.chat.core.model.UserRef
import org.springframework.stereotype.Component

@Component
class ChatCreatedMessageFactoryImpl() : MessageFactory<ChatDetails, ChatCreatedEvent> {

    override fun create(chat: ChatDetails): OutboxMessage<ChatCreatedEvent> {
        return OutboxMessage(
            key = chat.id.toString(),
            type = "ChatCreated",
            version = "v1",
            body = ChatCreatedEvent(
                id = chat.id,
                name = chat.name,
                owner = chat.owner?.asRef(),
                members = chat.members.map { it.asRef() },
                createdAt = chat.createdAt,
            )
        )
    }
}

private fun User.asRef(): UserRef = UserRef(id)
