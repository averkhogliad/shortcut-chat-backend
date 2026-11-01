package io.averkhoglyad.shortcut.outbox.autoconfigure

import io.averkhoglyad.shortcut.outbox.core.OutboxEntry
import io.averkhoglyad.shortcut.outbox.messaging.PublishResult
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture

class KafkaTemplateOutboxPublisherTest : FreeSpec({

    val kafkaTemplate = mockk<KafkaTemplate<String, ByteArray>>()
    val target = KafkaTemplateOutboxPublisher(kafkaTemplate)
    val entry = OutboxEntry(
        topic = "MailerCommands",
        key = "user-42",
        body = """{"to":"user@example.com"}""",
        headers = mapOf(
            OutboxHeaderNames.EVENT_ID to "018f0e3a-7c2b-7c3d-8f10-2b4c5d6e7f80",
            OutboxHeaderNames.EVENT_NAME to "SendEmail",
        ),
    )

    afterTest {
        clearAllMocks()
    }

    "publish" - {

        "sends a producer record with topic, key, body and headers" {
            // given
            every {
                kafkaTemplate.send(any<ProducerRecord<String, ByteArray>>())
            } returns CompletableFuture.completedFuture(mockk<SendResult<String, ByteArray>>())

            // when
            val result = target.publish(entry)

            // then
            result shouldBe PublishResult.Success

            val recordSlot = slot<ProducerRecord<String, ByteArray>>()
            verify(exactly = 1) { kafkaTemplate.send(capture(recordSlot)) }

            val record = recordSlot.captured
            record.topic() shouldBe "MailerCommands"
            record.key() shouldBe "user-42"
            record.value().toString(Charsets.UTF_8) shouldBe """{"to":"user@example.com"}"""
            record.headers().lastHeader(OutboxHeaderNames.EVENT_ID)
                .value().toString(Charsets.UTF_8) shouldBe "018f0e3a-7c2b-7c3d-8f10-2b4c5d6e7f80"
            record.headers().lastHeader(OutboxHeaderNames.EVENT_NAME)
                .value().toString(Charsets.UTF_8) shouldBe "SendEmail"
        }

        "returns a failure when KafkaTemplate throws" {
            // given
            every {
                kafkaTemplate.send(any<ProducerRecord<String, ByteArray>>())
            } throws KafkaException("broker unavailable")

            // when
            val result = target.publish(entry)

            // then
            val failure = result.shouldBeInstanceOf<PublishResult.Failure>()
            failure.error.shouldBeInstanceOf<KafkaException>()
            failure.error?.message shouldBe "broker unavailable"
        }
    }
})
