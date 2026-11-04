package io.averkhoglyad.shortcut.mail.sender.core.mailer

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.mail.SimpleMailMessage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class FileSystemBaseMailSenderTest : FreeSpec({

    lateinit var outputDir: Path
    lateinit var target: FileSystemBaseMailSender

    beforeSpec {
        outputDir = Files.createTempDirectory("mail-sender-test")
        target = FileSystemBaseMailSender(outputDir)
    }

    afterSpec {
        deleteRecursively(outputDir)
    }

    "send" - {
        "writes one eml file per recipient" {
            // given
            val message = message("first@example.com", "second@example.com")

            // when
            target.send(message)

            // then
            outputDir.resolve("first@example.com").emlFiles() shouldHaveSize 1
            outputDir.resolve("second@example.com").emlFiles() shouldHaveSize 1
        }

        "writes separate files for repeated send to the same recipient" {
            // given
            val message = message("repeated@example.com")

            // when
            target.send(message)
            target.send(message)

            // then
            val files = outputDir.resolve("repeated@example.com").emlFiles()
            files shouldHaveSize 2
            files.any { it.fileName.toString().contains("(1)") } shouldBe true
        }

        "uses null directory name when message has no recipients" {
            // given
            val message = SimpleMailMessage().apply {
                subject = "subject"
                text = "body"
            }

            // when
            target.send(message)

            // then
            outputDir.resolve("null").emlFiles() shouldHaveSize 1
        }

        "writes every message passed in a single send call" {
            // given
            val first = message("first-only@example.com")
            val second = message("second-only@example.com")

            // when
            target.send(first, second)

            // then
            outputDir.resolve("first-only@example.com").emlFiles() shouldHaveSize 1
            outputDir.resolve("second-only@example.com").emlFiles() shouldHaveSize 1
        }
    }
})

private fun message(vararg recipients: String) = SimpleMailMessage().apply {
    from = "from@example.com"
    setTo(*recipients)
    subject = "subject"
    text = "body"
}

private fun Path.emlFiles(): List<Path> =
    if (!Files.exists(this)) {
        emptyList()
    } else {
        Files.list(this).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".eml") }.toList()
        }
    }

private fun deleteRecursively(path: Path) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (Files.exists(path)) {
        runCatching { path.toFile().deleteRecursively() }
        if (!Files.exists(path)) return
        if (System.nanoTime() > deadline) {
            throw IllegalStateException("Failed to delete $path")
        }
        Thread.sleep(50)
    }
}
