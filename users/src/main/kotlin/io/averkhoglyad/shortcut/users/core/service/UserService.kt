package io.averkhoglyad.shortcut.users.core.service

import io.averkhoglyad.shortcut.common.data.EntityResult
import io.averkhoglyad.shortcut.common.persistence.transaction.transaction
import io.averkhoglyad.shortcut.common.util.slf4j
import io.averkhoglyad.shortcut.outbox.autoconfigure.OutboxEmitter
import io.averkhoglyad.shortcut.users.core.converter.UserConverter
import io.averkhoglyad.shortcut.users.core.model.User
import io.averkhoglyad.shortcut.users.core.model.UserDeleted
import io.averkhoglyad.shortcut.users.core.persistence.entity.UserEntity
import io.averkhoglyad.shortcut.users.core.persistence.repository.UserRepository
import io.averkhoglyad.shortcut.users.core.service.message.OutboxMessage
import io.averkhoglyad.shortcut.users.core.service.message.SendCreatedUserNotificationMessageFactoryImpl
import io.averkhoglyad.shortcut.users.core.service.message.UserCreatedMessageFactoryImpl
import io.averkhoglyad.shortcut.users.core.service.message.UserDeletedMessageFactoryImpl
import io.averkhoglyad.shortcut.users.core.service.message.UserUpdatedMessageFactoryImpl
import io.micrometer.core.instrument.Counter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

interface UserService {

    fun find(id: UUID): EntityResult<User>
    fun findByEmail(email: String): EntityResult<User>
    fun create(user: User): EntityResult<User>
    fun update(user: User): EntityResult<User>
    fun delete(id: UUID): EntityResult<Unit>

}

@Service
class UserServiceImpl(
    private val repository: UserRepository,
    private val converter: UserConverter,
    private val userCreatedMessageFactory: UserCreatedMessageFactoryImpl,
    private val userUpdatedMessageFactory: UserUpdatedMessageFactoryImpl,
    private val userDeletedMessageFactory: UserDeletedMessageFactoryImpl,
    private val sendCreatedUserNotificationMessageFactory: SendCreatedUserNotificationMessageFactoryImpl,
    private val outboxEmitter: OutboxEmitter,
    private val userRegistrationCounter: Counter,
): UserService {

    private val logger by slf4j()

    @Transactional(readOnly = true)
    override fun find(id: UUID): EntityResult<User> {
        return repository.findById(id)
            ?.let { EntityResult.Success(it.toModel()) }
            ?: EntityResult.NotFound
    }

    @Transactional(readOnly = true)
    override fun findByEmail(email: String): EntityResult<User> {
        return repository.findByEmail(email)
            ?.let { EntityResult.Success(it.toModel()) }
            ?: EntityResult.NotFound
    }

    @Transactional
    override fun create(user: User): EntityResult<User> {
        require(user.id == null)

        return repository.save(user.toEntity())
            .toModel()
            .also { emitUserCreatedLifecycleEvent(it) }
            .also { emitUserCreatedStreamingEvent(it) }
            .also { sendNotification(it) }
            .also { incrementUserRegistrationMetric() }
            .let { EntityResult.Success(it) }
    }

    private fun sendNotification(user: User) {
        emit(userCreatedMessageFactory.create(user))
    }

    private fun emitUserCreatedLifecycleEvent(user: User) {
        emit(sendCreatedUserNotificationMessageFactory.create(user))
    }

    private fun emitUserCreatedStreamingEvent(user: User) {
        logger.info("User ${user.id} created - streaming event")
    }

    private fun emit(message: OutboxMessage<*>) {
        outboxEmitter.emit(message.type, message.version, message.key, message.body)
    }

    private fun incrementUserRegistrationMetric() {
        transaction {
            afterCommit {
                userRegistrationCounter.increment()
            }
        }
    }

    @Transactional
    override fun update(user: User): EntityResult<User> {
        requireNotNull(user.id)

        val entity = repository.findById(user.id)
            ?: return EntityResult.NotFound

        if (entity.email != user.email) {
            return EntityResult.Invalid("Email is immutable")
        }

        return repository.save(user.toEntity(entity))
            .toModel()
            .also { emit(userUpdatedMessageFactory.create(it)) }
            .also { logger.info("User ${it.id} updated - streaming event") }
            .let { EntityResult.Success(it) }
    }

    @Transactional
    override fun delete(id: UUID): EntityResult<Unit> {
        if (repository.findById(id) == null) {
            return EntityResult.NotFound
        }

        repository.deleteById(id)
        emit(userDeletedMessageFactory.create(UserDeleted(id)))

        return EntityResult.Success(Unit)
    }

    private fun User.toEntity(): UserEntity = converter.toEntity(this)

    private fun User.toEntity(entity: UserEntity): UserEntity = converter.toEntity(this, entity)

    private fun UserEntity.toModel(): User = converter.fromEntity(this)
}
