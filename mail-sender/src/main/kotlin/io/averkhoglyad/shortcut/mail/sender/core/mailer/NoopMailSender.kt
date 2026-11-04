package io.averkhoglyad.shortcut.mail.sender.core.mailer

import org.slf4j.LoggerFactory
import org.springframework.mail.MailSender
import org.springframework.mail.SimpleMailMessage

class NoopMailSender : MailSender {

    private val logger = LoggerFactory.getLogger(NoopMailSender::class.java)

    override fun send(vararg simpleMessages: SimpleMailMessage) {
        simpleMessages
            .filterNotNull()
            .forEach { message -> writeMessageToLog(message) }
    }

    private fun writeMessageToLog(message: SimpleMailMessage) {
        logger.info("Message sent by noop sender:\n{}", message)
    }
}
