package io.averkhoglyad.shortcut.mail.sender.core.mailer

import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.MailSender
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.MimeMailMessage
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.text.MessageFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")
private const val TPL_FILE_NAME_DEFAULT = "{0}.eml"
private const val TPL_FILE_NAME_INDEXED = "{0} ({1}).eml"

@Component
@ConditionalOnProperty("sender.mail.output-dir")
class FileSystemBaseMailSender(
    @Value(value = "\${sender.mail.output-dir:/tmp}")
    private val outputPath: Path
) : MailSender {

    override fun send(vararg simpleMessages: SimpleMailMessage) {
        simpleMessages
            .filterNotNull()
            .flatMap { message -> (message.to ?: arrayOf("null")).map { recipient -> recipient to message } }
            .forEach { (recipient, message) -> writeMessageToFile(recipient, message) }
    }

    private fun writeMessageToFile(recipient: String, message: SimpleMailMessage) {
        val path: Path = createFileToWriteMessage(recipient)
        Files.createDirectories(path.parent)
        Files.newOutputStream(path)
            .use {
                MimeMailMessage(MimeMessage(null as Session?))
                    .apply { message.copyTo(this) }
                    .mimeMessage
                    .writeTo(it)
                it.flush()
            }
    }

    private fun createFileToWriteMessage(address: String): Path {
        val recipientDirPath = outputPath.resolve(address)
        val date = LocalDateTime.now().format(DATE_TIME_FORMATTER)
        var path: Path
        var inc = 0
        do {
            path = recipientDirPath.resolve(createFileName(date, inc++))
        } while (Files.isRegularFile(path))
        return path
    }

    private fun createFileName(date: String, index: Int) =
        if (index == 0) MessageFormat.format(TPL_FILE_NAME_DEFAULT, date)
        else MessageFormat.format(TPL_FILE_NAME_INDEXED, date, index)
}