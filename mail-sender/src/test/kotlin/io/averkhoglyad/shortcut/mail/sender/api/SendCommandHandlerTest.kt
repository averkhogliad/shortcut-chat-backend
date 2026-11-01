package io.averkhoglyad.shortcut.mail.sender.api

import io.averkhoglyad.shortcut.mail.sender.core.mailer.EmailMessage
import io.averkhoglyad.shortcut.mail.sender.core.service.MailSendService
import io.averkhoglyad.shortcut.mail.sender.test.emailMessages
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.arbitrary.next
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import java.util.*

class SendCommandHandlerTest : FreeSpec({

    val service = mockk<MailSendService>()
    val target = SendCommandHandler(service)

    afterTest {
        clearAllMocks()
    }

    "handleMailerCommand" - {
        "passes event id as idempotency key and message payload to service" {
            // given
            val eventId = UUID.randomUUID().toString()
            val message = emailMessages.next()
            every { service.send(any(), any()) } just Runs

            // when
            target.handleMailerCommand(eventId, message)

            // then
            val eventIdSlot = slot<String>()
            val messageSlot = slot<EmailMessage>()
            verify(exactly = 1) { service.send(capture(eventIdSlot), capture(messageSlot)) }
            eventIdSlot.captured shouldBe eventId
            messageSlot.captured shouldBeSameInstanceAs message
        }
    }
})
