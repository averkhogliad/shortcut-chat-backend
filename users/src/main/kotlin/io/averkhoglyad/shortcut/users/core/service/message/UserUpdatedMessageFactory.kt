package io.averkhoglyad.shortcut.users.core.service.message

import io.averkhoglyad.shortcut.users.core.model.User
import org.springframework.stereotype.Component

@Component
class UserUpdatedMessageFactoryImpl() : MessageFactory<User, User> {

    override fun create(user: User): OutboxMessage<User> {
        return OutboxMessage(
            type = "UserUpdated",
            version = "v1",
            body = user
        )
    }
}
