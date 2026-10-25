package io.averkhoglyad.shortcut.mail.sender.core.mailer

import io.averkhoglyad.shortcut.mail.sender.core.persistence.MessageEntity
import io.averkhoglyad.shortcut.mail.sender.core.persistence.MessageEntity.Status
import io.averkhoglyad.shortcut.mail.sender.core.persistence.MessageRepository
import io.averkhoglyad.shortcut.mail.sender.core.service.MailSendService
import io.averkhoglyad.shortcut.mail.sender.test.emailMessages
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.email
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import io.micrometer.core.instrument.Counter
import io.mockk.*
import org.springframework.mail.MailSender
import org.springframework.mail.SimpleMailMessage
import java.time.Instant
import java.util.*

class MailSendServiceTest : FreeSpec({

    val emailSender = mockk<MailSender>()
    val messageFactory = mockk<MailMessageFactory>()
    val fromEmail = Arb.email().next()
    val repository = mockk<MessageRepository>()
    val sentMailCounter = mockk<Counter>()

    afterTest {
        clearAllMocks()
    }

    val service = MailSendService(
        emailSender = emailSender,
        messageFactory = messageFactory,
        from = fromEmail,
        repository = repository,
        sentMailCounter = sentMailCounter,
    )

    "send" - {
        "throws IllegalArgumentException if no receiver email" {
            val eventId = UUID.randomUUID().toString()
            val message = emailMessages.next().copy(to = emptyList())
            shouldThrow<IllegalArgumentException> {
                service.send(eventId, message)
            }
        }

        "creates MailMessage and pass to MailSender" {
            checkAll(emailMessages) { message ->
                // given
                val eventId = UUID.randomUUID().toString()
                val mailMessageForSender = SimpleMailMessage()
                every { messageFactory.create(any(), any()) } returns mailMessageForSender
                every { emailSender.send(any()) } just runs
                every { repository.findById(any()) } returns null
                every { repository.save(any()) } answers { args[0] as MessageEntity }
                every { sentMailCounter.increment() } just runs

                // when
                service.send(eventId, message)

                // then
                verify { messageFactory.create(fromEmail, message) }
                verify { sentMailCounter.increment() }

                val mailMessageSlot = slot<SimpleMailMessage>()
                verify { emailSender.send(capture(mailMessageSlot)) }
                mailMessageSlot.captured should {
                    it shouldBeSameInstanceAs mailMessageForSender
                }

                verify { repository.findById(eventId) }

                val entitySlot = slot<MessageEntity>()
                verify { repository.save(capture(entitySlot)) }
                entitySlot.captured should {
                    it.id shouldBeEqual eventId
                    it.payload shouldBeEqual message
                    it.status shouldBeEqual Status.COMPLETED
                    it.exception shouldBe null
                }

                // afterTest
                clearAllMocks()
                confirmVerified(messageFactory, emailSender, repository, sentMailCounter)
            }
        }

        "does nothing if event already completed" {
            checkAll(emailMessages) { message ->
                // given
                val eventId = UUID.randomUUID().toString()
                val entity = MessageEntity(
                    id = eventId,
                    payload = message,
                    status = Status.COMPLETED,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                )
                every { repository.findById(any()) } returns entity

                // when
                service.send(eventId, message)

                // then
                verify { repository.findById(eventId) }

                // afterTest
                clearAllMocks()
            }
        }

        "saves event as failed on exception" {
            checkAll(emailMessages) { message ->
                // given
                val eventId = UUID.randomUUID().toString()
                val mailMessageForSender = SimpleMailMessage()
                val exception  = Exception()
                every { messageFactory.create(any(), any()) } returns mailMessageForSender
                every { emailSender.send(any()) } throws exception
                every { repository.findById(any()) } returns null
                every { repository.save(any()) } answers { args[0] as MessageEntity }

                // when
                service.send(eventId, message)

                // then
                verify { messageFactory.create(fromEmail, message) }

                val mailMessageSlot = slot<SimpleMailMessage>()
                verify { emailSender.send(capture(mailMessageSlot)) }
                mailMessageSlot.captured should {
                    it shouldBeSameInstanceAs mailMessageForSender
                }

                verify { repository.findById(eventId) }

                val entitySlot = slot<MessageEntity>()
                verify { repository.save(capture(entitySlot)) }
                entitySlot.captured should {
                    it.id shouldBeEqual eventId
                    it.payload shouldBeEqual message
                    it.status shouldBeEqual Status.FAILED
                    it.exception shouldBe exception
                }

                // afterTest
                clearAllMocks()
            }
        }
    }
})
