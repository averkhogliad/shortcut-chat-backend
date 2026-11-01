package io.averkhoglyad.shortcut.chat.outbox

import io.averkhoglyad.shortcut.chat.core.persistence.repository.OutboxMessageRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Headers
import org.springframework.kafka.core.KafkaTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.*

class MessageOutboxHandlerImplTest : FreeSpec({

    val kafkaTemplate = mockk<KafkaTemplate<String, ByteArray>>(relaxed = true)
    val repository = mockk<OutboxMessageRepository>()
    val now = Instant.parse("2026-01-01T00:00:00Z")
    val clock = Clock.fixed(now, ZoneOffset.UTC)
    val target = MessageOutboxHandlerImpl(
        kafkaTemplate = kafkaTemplate,
        repository = repository,
        portionSize = 128,
        applicationName = "chats-app",
        clock = clock,
    )

    afterTest {
        clearAllMocks()
    }

    "handleNext" - {
        "publishes pending messages to ChatLifecycle with headers and marks them published" {
            // given
            val first = outboxMessage(
                type = "ChatCreated",
                key = "first-chat",
                body = byteArrayOf(1, 2, 3),
            )
            val second = outboxMessage(
                type = "ChatCreated",
                key = "second-chat",
                body = byteArrayOf(4, 5, 6),
            )
            every {
                repository.findByCreatedAtLessThanAndPublishedAtIsNullOrderByCreatedAt(now, any())
            } returnsMany listOf(listOf(first, second), emptyList<io.averkhoglyad.shortcut.chat.core.persistence.entity.OutboxMessageEntity>())
            every { repository.markAsPublishedByIdIsIn(any()) } just Runs

            // when
            target.handleNext()

            // then
            val records = mutableListOf<ProducerRecord<String, ByteArray>>()
            verify(exactly = 2) { kafkaTemplate.send(capture(records)) }

            records shouldHaveSize 2
            records[0].topic() shouldBe "ChatLifecycle"
            records[0].key() shouldBe "first-chat"
            records[0].value().toList() shouldBe byteArrayOf(1, 2, 3).toList()
            records[0].headers().verifyHeaders(first)
            records[1].key() shouldBe "second-chat"
            records[1].value().toList() shouldBe byteArrayOf(4, 5, 6).toList()
            records[1].headers().verifyHeaders(second)

            val markedIds = mutableListOf<List<UUID>>()
            verify(exactly = 1) { repository.markAsPublishedByIdIsIn(capture(markedIds)) }
            markedIds.single() shouldBe listOf(first.id, second.id)
        }

        "processes every non-empty portion until repository returns no messages" {
            // given
            val first = outboxMessage()
            val second = outboxMessage()
            every {
                repository.findByCreatedAtLessThanAndPublishedAtIsNullOrderByCreatedAt(now, any())
            } returnsMany listOf(listOf(first), listOf(second), emptyList<io.averkhoglyad.shortcut.chat.core.persistence.entity.OutboxMessageEntity>())
            every { repository.markAsPublishedByIdIsIn(any()) } just Runs

            // when
            target.handleNext()

            // then
            verify(exactly = 2) { kafkaTemplate.send(any<ProducerRecord<String, ByteArray>>()) }
            verify(exactly = 2) { repository.markAsPublishedByIdIsIn(any()) }
        }

        "fails without publishing when message type is unknown" {
            // given
            val message = outboxMessage(type = "UnexpectedType")
            every {
                repository.findByCreatedAtLessThanAndPublishedAtIsNullOrderByCreatedAt(now, any())
            } returns listOf(message)

            // when
            val error = shouldThrow<IllegalStateException> {
                target.handleNext()
            }

            // then
            error.message shouldBe "Unexpected message type UnexpectedType"
            verify(exactly = 0) { kafkaTemplate.send(any<ProducerRecord<String, ByteArray>>()) }
            verify(exactly = 0) { repository.markAsPublishedByIdIsIn(any()) }
        }

        "does nothing when there are no pending messages" {
            // given
            every {
                repository.findByCreatedAtLessThanAndPublishedAtIsNullOrderByCreatedAt(now, any())
            } returns emptyList()

            // when
            target.handleNext()

            // then
            verify(exactly = 0) { kafkaTemplate.send(any<ProducerRecord<String, ByteArray>>()) }
            verify(exactly = 0) { repository.markAsPublishedByIdIsIn(any()) }
        }
    }
})

private fun Headers.verifyHeaders(message: io.averkhoglyad.shortcut.chat.core.persistence.entity.OutboxMessageEntity) {
    headerAsString(EVENT_ID) shouldBe message.id.toString()
    headerAsString(EVENT_NAME) shouldBe "ChatCreated"
    headerAsString(EVENT_VERSION) shouldBe message.version
    headerAsString(PUBLISHED_AT) shouldBe "2026-01-01T00:00:00Z"
    headerAsString(PUBLISHED_BY) shouldBe "chats-app"
}

private fun Headers.headerAsString(name: String): String? =
    lastHeader(name)?.value()?.toString(Charsets.UTF_8)

