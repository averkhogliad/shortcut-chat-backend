package io.averkhoglyad.shortcut.users.core.service

import io.averkhoglyad.shortcut.outbox.autoconfigure.OutboxEmitter
import io.averkhoglyad.shortcut.users.core.model.EmailMessage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.email
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class OtpRequestServiceTest : FreeSpec({

    val otpService = mockk<OtpService>()
    val outboxEmitter = mockk<OutboxEmitter>()

    val service = OtpRequestServiceImpl(otpService, outboxEmitter)

    afterTest {
        clearAllMocks()
    }

    "requestCode" - {

        "emits exactly one SendOtpCode message with the requested address and the generated code" {
            // given
            val email = Arb.email().next()
            val code = Arb.string(6).next()
            every { otpService.requestCode(any()) } returns OtpRequestResult.CodeSent(code)
            every { outboxEmitter.emit(any(), any(), null, any()) } just Runs

            // when
            val result = service.requestCode(email)

            // then
            result shouldBe OtpRequestResult.CodeSent(code)

            val requestedEmail = slot<String>()
            verify(exactly = 1) { otpService.requestCode(capture(requestedEmail)) }
            requestedEmail.captured shouldBe email

            val type = slot<String>()
            val version = slot<String>()
            val body = slot<Any>()
            verify(exactly = 1) {
                outboxEmitter.emit(capture(type), capture(version), null, capture(body))
            }
            type.captured shouldBe "SendOtpCode"
            version.captured shouldBe "v1"
            body.captured shouldBe EmailMessage(
                to = listOf(email),
                subject = "Your code",
                body = code,
            )
        }

        "emits no message and keeps the rate limited result when the email is rate limited" {
            // given
            val email = Arb.email().next()
            every { otpService.requestCode(any()) } returns OtpRequestResult.RateLimited

            // when
            val result = service.requestCode(email)

            // then
            result shouldBe OtpRequestResult.RateLimited

            val requestedEmail = slot<String>()
            verify(exactly = 1) { otpService.requestCode(capture(requestedEmail)) }
            requestedEmail.captured shouldBe email

            verify(exactly = 0) { outboxEmitter.emit(any(), any(), any(), any()) }
        }

        "propagates a failure to request the code and emits no message" {
            // given
            val email = Arb.email().next()
            every { otpService.requestCode(any()) } throws IllegalStateException("otp storage failed")

            // when
            shouldThrow<IllegalStateException> {
                service.requestCode(email)
            }

            // then
            verify(exactly = 0) { outboxEmitter.emit(any(), any(), any(), any()) }
        }
    }
})
