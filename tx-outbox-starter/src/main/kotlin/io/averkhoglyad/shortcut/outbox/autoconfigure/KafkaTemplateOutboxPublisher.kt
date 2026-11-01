package io.averkhoglyad.shortcut.outbox.autoconfigure

import io.averkhoglyad.shortcut.outbox.core.OutboxEntry
import io.averkhoglyad.shortcut.outbox.messaging.OutboxPublisher
import io.averkhoglyad.shortcut.outbox.messaging.PublishResult
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.kafka.core.KafkaTemplate
import java.nio.charset.StandardCharsets

class KafkaTemplateOutboxPublisher(
    private val kafkaTemplate: KafkaTemplate<String, ByteArray>,
) : OutboxPublisher {

    override fun publish(entry: OutboxEntry): PublishResult {
        return try {
            val record = ProducerRecord<String, ByteArray>(
                entry.topic,
                entry.key,
                entry.body.toByteArray(StandardCharsets.UTF_8),
            )
            entry.headers.forEach { (name, value) ->
                record.headers().add(name, value.toByteArray(StandardCharsets.UTF_8))
            }
            kafkaTemplate.send(record)
            PublishResult.Success
        } catch (error: Exception) {
            PublishResult.Failure(error)
        }
    }
}
