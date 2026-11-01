package io.averkhoglyad.shortcut.chat.outbox

import io.averkhoglyad.shortcut.chat.TestcontainersConfiguration
import io.averkhoglyad.shortcut.chat.core.model.ChatCreatedEvent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID.randomUUID

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class OutboxServiceImplIntegrationTest(
    private val target: OutboxService,
    private val jdbc: JdbcClient,
    private val transactionManager: PlatformTransactionManager,
) : FreeSpec({

    beforeEach { jdbc.clearOutbox() }
    afterEach { jdbc.clearOutbox() }

    "save" - {
        "fails when called outside transaction" {
            // given
            val message = chatCreatedMessage()

            // when
            shouldThrow<IllegalTransactionStateException> {
                target.save(message)
            }

            // then
            jdbc.countOutbox() shouldBe 0L
        }

        "writes outbox message inside transaction" {
            // given
            val message = chatCreatedMessage()

            // when
            TransactionTemplate(transactionManager).execute {
                target.save(message)
                jdbc.countOutbox()
            } shouldBe 1L

            // then
            jdbc.countOutbox() shouldBe 1L
        }
    }
})

private fun chatCreatedMessage() = OutboxMessage(
    type = "ChatCreated",
    version = "v1",
    key = randomUUID().toString(),
    body = ChatCreatedEvent(
        id = randomUUID(),
        name = "chat",
        owner = null,
        members = emptyList(),
        createdAt = Instant.now(),
    ),
)

private fun JdbcClient.countOutbox(): Long =
    this.sql("select count(*) from message_outbox")
        .query(Long::class.java)
        .single()

private fun JdbcClient.clearOutbox() {
    this.sql("delete from message_outbox").update()
}
