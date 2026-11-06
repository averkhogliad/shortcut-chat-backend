package io.averkhoglyad.shortcut.users.core.service

import io.averkhoglyad.shortcut.outbox.autoconfigure.OutboxEmitter
import io.averkhoglyad.shortcut.users.core.model.EmailMessage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val SEND_OTP_CODE_MESSAGE_TYPE = "SendOtpCode"
private const val MESSAGE_VERSION = "v1"
private const val OTP_EMAIL_SUBJECT = "Your code"

interface OtpRequestService {

    fun requestCode(email: String): OtpRequestResult

}

@Service
class OtpRequestServiceImpl(
    private val otpService: OtpService,
    private val outboxEmitter: OutboxEmitter,
) : OtpRequestService {

    // The outbox writer requires an active transaction (propagation MANDATORY): the code email
    // and its outbox record are one unit of work, committed before the endpoint replies 202.
    @Transactional
    override fun requestCode(email: String): OtpRequestResult =
        when (val result = otpService.requestCode(email)) {
            is OtpRequestResult.CodeSent -> result
                .also { emitOtpCode(email, it.code) }
            OtpRequestResult.RateLimited -> result
        }

    private fun emitOtpCode(email: String, code: String) {
        outboxEmitter.emit(
            type = SEND_OTP_CODE_MESSAGE_TYPE,
            version = MESSAGE_VERSION,
            key = null,
            bodyPayload = EmailMessage(
                to = listOf(email),
                subject = OTP_EMAIL_SUBJECT,
                body = code,
            ),
        )
    }
}
