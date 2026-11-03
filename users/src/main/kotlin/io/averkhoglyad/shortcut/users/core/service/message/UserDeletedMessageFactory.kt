package io.averkhoglyad.shortcut.users.core.service.message

import io.averkhoglyad.shortcut.users.core.model.UserDeleted
import org.springframework.stereotype.Component

@Component
class UserDeletedMessageFactoryImpl() : MessageFactory<UserDeleted, UserDeleted> {

    override fun create(user: UserDeleted): OutboxMessage<UserDeleted> {
        return OutboxMessage(
            type = "UserDeleted",
            version = "v1",
            body = user
        )
    }
}
