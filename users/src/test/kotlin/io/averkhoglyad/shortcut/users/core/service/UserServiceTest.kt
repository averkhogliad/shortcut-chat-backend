package io.averkhoglyad.shortcut.users.core.service

import io.averkhoglyad.shortcut.common.data.EntityResult.NotFound
import io.averkhoglyad.shortcut.common.data.EntityResult.Success
import io.averkhoglyad.shortcut.outbox.autoconfigure.OutboxEmitter
import io.averkhoglyad.shortcut.users.core.converter.UserConverter
import io.averkhoglyad.shortcut.users.core.model.EmailMessage
import io.averkhoglyad.shortcut.users.core.model.User
import io.averkhoglyad.shortcut.users.core.persistence.repository.UserRepository
import io.averkhoglyad.shortcut.users.core.service.message.OutboxMessage
import io.averkhoglyad.shortcut.users.core.service.message.SendCreatedUserNotificationMessageFactoryImpl
import io.averkhoglyad.shortcut.users.core.service.message.UserCreatedMessageFactoryImpl
import io.averkhoglyad.shortcut.users.test.gen.userEntities
import io.averkhoglyad.shortcut.users.test.gen.users
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.arbitrary.next
import io.micrometer.core.instrument.Counter
import io.mockk.*
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCommit
import java.util.UUID.randomUUID

class UserServiceTest : FreeSpec({

    val repository = mockk<UserRepository>()
    val converter= mockk<UserConverter>()
    val userCreatedMessageFactory = mockk<UserCreatedMessageFactoryImpl>()
    val sendNotificationMessageFactory= mockk<SendCreatedUserNotificationMessageFactoryImpl>()
    val outboxEmitter = mockk<OutboxEmitter>(relaxed = true)
    val userRegistrationCount= mockk<Counter>()

    afterTest {
        clearAllMocks()
    }

    val service = UserServiceImpl(
        repository = repository,
        converter = converter,
        userCreatedMessageFactory = userCreatedMessageFactory,
        sendCreatedUserNotificationMessageFactory = sendNotificationMessageFactory,
        outboxEmitter = outboxEmitter,
        userRegistrationCounter = userRegistrationCount,
    )

    "find" - {
        "returns NotFound response on wrong id" {
            // given
            val id = randomUUID()
            every { repository.findById(any()) } returns null

            // when
            val result = service.find(id)

            // then
            result shouldBe NotFound

            verify { repository.findById(id) }
            confirmVerified(repository)
        }

        "returns Success response with found user" {
            // given
            val id = randomUUID()
            val entity = userEntities.next()
            val user = users.next()

            every { repository.findById(any()) } returns entity
            every { converter.fromEntity(any()) } returns user

            // when
            val result = service.find(id)

            // then
            result.shouldBeInstanceOf<Success<User>>()
            result.entity shouldBeSameInstanceAs user

            verify { repository.findById(id) }
            verify { converter.fromEntity(entity) }
            confirmVerified(repository, converter)
        }
    }

    "create" - {
        beforeEach { TransactionSynchronizationManager.initSynchronization() }
        afterEach { TransactionSynchronizationManager.clearSynchronization() }

        "returns Success response with created user and emits outbox messages" {
            // given
            val entity = userEntities.next()
            val input = users.next().copy(id = null)
            val output = users.next()
            val notificationBody = EmailMessage(
                to = listOf(output.email),
                subject = "Welcome ${output.name}!",
                body = "Hello ${output.name}. Welcome to our system",
            )
            val notificationMessage = OutboxMessage("SendCreatedUserNotification", "v1", null, notificationBody)
            val userCreatedMessage = OutboxMessage("UserCreated", "v1", null, output)

            every { repository.save(any()) } returns entity
            every { converter.toEntity(any()) } returns entity
            every { converter.fromEntity(any()) } returns output
            every { userCreatedMessageFactory.create(any()) } returns userCreatedMessage
            every { sendNotificationMessageFactory.create(any()) } returns notificationMessage
            every { userRegistrationCount.increment() } just runs

            // when
            val result = service.create(input)
            triggerAfterCommit()

            // then
            result.shouldBeInstanceOf<Success<User>>()
            result.entity shouldBeSameInstanceAs output

            verify { repository.save(entity) }
            verifyAll {
                converter.toEntity(input)
                converter.fromEntity(entity)
            }
            verify { userCreatedMessageFactory.create(output) }
            verify { sendNotificationMessageFactory.create(output) }

            val notificationType = slot<String>()
            val notificationVersion = slot<String>()
            val notificationKey = slot<String?>()
            val notificationPayload = slot<Any>()
            val userCreatedType = slot<String>()
            val userCreatedVersion = slot<String>()
            val userCreatedKey = slot<String?>()
            val userCreatedPayload = slot<Any>()

            verifyOrder {
                outboxEmitter.emit(
                    capture(notificationType),
                    capture(notificationVersion),
                    captureNullable(notificationKey),
                    capture(notificationPayload),
                )
                outboxEmitter.emit(
                    capture(userCreatedType),
                    capture(userCreatedVersion),
                    captureNullable(userCreatedKey),
                    capture(userCreatedPayload),
                )
            }

            notificationType.captured shouldBe "SendCreatedUserNotification"
            notificationVersion.captured shouldBe "v1"
            notificationKey.captured shouldBe null
            notificationPayload.captured shouldBeSameInstanceAs notificationBody

            userCreatedType.captured shouldBe "UserCreated"
            userCreatedVersion.captured shouldBe "v1"
            userCreatedKey.captured shouldBe null
            userCreatedPayload.captured shouldBeSameInstanceAs output

            verify { userRegistrationCount.increment() }

            confirmVerified(repository, converter, userCreatedMessageFactory, sendNotificationMessageFactory, outboxEmitter, userRegistrationCount)
        }
    }

    "update" - {
        "returns NotFound response on wrong id" {
            // given
            val id = randomUUID()
            val user = users.next().copy(id = id)
            every { repository.findById(id) } returns null

            // when
            val result = service.update(user)

            // then
            result shouldBe NotFound

            verify { repository.findById(id) }
            confirmVerified(repository)
        }

        "returns Success response with updated user" {
            // given
            val id = randomUUID()
            val entity = userEntities.next()
            val input = users.next().copy(id = id)
            val output = users.next()

            every { repository.findById(any()) } returns entity
            every { repository.save(any()) } returns entity
            every { converter.toEntity(any(), any()) } returns entity
            every { converter.fromEntity(any()) } returns output

            // when
            val result = service.update(input)

            // then
            result.shouldBeInstanceOf<Success<User>>()
            result.entity shouldBeSameInstanceAs output

            verify { repository.findById(id) }
            verify { repository.save(entity) }
            verify { converter.toEntity(input, entity) }
            verify { converter.fromEntity(entity) }

            confirmVerified(repository, converter)
        }
    }
}) {
    init {
        failfast
    }
}
