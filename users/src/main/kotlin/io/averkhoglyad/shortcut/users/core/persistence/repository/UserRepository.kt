package io.averkhoglyad.shortcut.users.core.persistence.repository

import io.averkhoglyad.shortcut.users.core.persistence.entity.UserEntity
import org.springframework.data.repository.Repository
import java.util.*

interface UserRepository: Repository<UserEntity, UUID> {

    fun findById(id: UUID): UserEntity?

    fun findByEmail(email: String): UserEntity?

    fun save(user: UserEntity): UserEntity

    fun deleteById(id: UUID)

}
