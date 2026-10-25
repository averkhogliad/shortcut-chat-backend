package io.averkhoglyad.shortcut.users.core.service

import io.averkhoglyad.shortcut.common.data.EntityResult.NotFound
import io.averkhoglyad.shortcut.common.data.EntityResult.Success
import io.averkhoglyad.shortcut.users.core.converter.UserConverter
import io.averkhoglyad.shortcut.users.core.model.EmailMessage
import io.averkhoglyad.shortcut.users.core.model.User
import io.averkhoglyad.shortcut.users.core.persistence.repository.UserRepository
import io.averkhoglyad.shortcut.users.core.service.message.SendCreatedUserNotificationMessageFactoryImpl
import io.averkhoglyad.shortcut.users.core.service.message.UserCreatedMessageFactoryImpl
import io.averkhoglyad.shortcut.users.outbox.OutboxMessage
import io.averkhoglyad.shortcut.users.outbox.OutboxService
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
    val messageOutboxService= mockk<OutboxService>()
    val userRegistrationCount= mockk<Counter>()

    afterTest {
        clearAllMocks()
    }

    val service = UserServiceImpl(
        repository = repository,
        converter = converter,
        userCreatedMessageFactory = userCreatedMessageFactory,
        sendCreatedUserNotificationMessageFactory = sendNotificationMessageFactory,
        messageOutboxService = messageOutboxService,
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
        
        "returns Success response with created user" {
            // given
            val entity = userEntities.next()
            val input = users.next().copy(id = null)
            val output = users.next()
            val sendNotificationMessage = mockk<OutboxMessage<EmailMessage>>()
            val userCreatedMessage = mockk<OutboxMessage<User>>()

            every { repository.save(any()) } returns entity
            every { converter.toEntity(any()) } returns entity
            every { converter.fromEntity(any()) } returns output
            every { userCreatedMessageFactory.create(any()) } returns userCreatedMessage
            every { sendNotificationMessageFactory.create(any()) } returns sendNotificationMessage
            every { messageOutboxService.saveMessage(any()) } just runs
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
            verifyAll {
                messageOutboxService.saveMessage(userCreatedMessage)
                messageOutboxService.saveMessage(sendNotificationMessage)
            }
            verify { userRegistrationCount.increment() }

            confirmVerified(repository, converter, userCreatedMessageFactory, sendNotificationMessageFactory, messageOutboxService, userRegistrationCount)
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
