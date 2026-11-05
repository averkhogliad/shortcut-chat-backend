package io.averkhoglyad.shortcut.chat.core.persistence.repository

import io.averkhoglyad.shortcut.chat.core.persistence.entity.UserEntity
import org.springframework.data.repository.Repository
import java.util.*

interface UserRepository : Repository<UserEntity, UUID> {

    fun findById(id: UUID): UserEntity?

    fun findByIdIsIn(ids: Collection<UUID>): Collection<UserEntity>

    fun findByIdIsInAndDeletedFalse(ids: Collection<UUID>): Collection<UserEntity>

    fun save(user: UserEntity): UserEntity

}