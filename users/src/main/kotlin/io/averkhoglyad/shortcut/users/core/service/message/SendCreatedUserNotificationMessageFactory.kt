package io.averkhoglyad.shortcut.users.core.service.message

import io.averkhoglyad.shortcut.users.core.model.EmailMessage
import io.averkhoglyad.shortcut.users.core.model.User
import org.springframework.stereotype.Component

@Component
class SendCreatedUserNotificationMessageFactoryImpl() : MessageFactory<User, EmailMessage> {

    override fun create(user: User): OutboxMessage<EmailMessage> {
        val emailMessage = EmailMessage(
            to = listOf(user.email),
            subject = "Welcome ${user.name}!",
            body = "Hello ${user.name}. Welcome to our system" // TODO: Here must be used some template engine
        )

        return OutboxMessage(
            type = "SendCreatedUserNotification",
            version = "v1",
            body = emailMessage
        )
    }
}
