package io.averkhoglyad.shortcut.chat.core.converter

import io.averkhoglyad.shortcut.chat.core.model.User
import io.averkhoglyad.shortcut.chat.core.persistence.entity.UserEntity
import org.springframework.stereotype.Component

interface UserConverter {

    fun toDetails(entity: UserEntity): User

}

@Component
class UserConverterImpl : UserConverter {
    override fun toDetails(entity: UserEntity): User {
        return User(
            id = requireNotNull(entity.id),
            name = entity.name,
            email = entity.email,
            deleted = entity.deleted,
        )
    }
}
