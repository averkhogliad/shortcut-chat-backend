package io.averkhoglyad.shortcut.chat.config

import io.averkhoglyad.shortcut.outbox.autoconfigure.KafkaTemplateOutboxPublisher
import io.averkhoglyad.shortcut.outbox.core.OutboxEntry
import io.averkhoglyad.shortcut.outbox.messaging.OutboxPublisher
import io.averkhoglyad.shortcut.outbox.messaging.PublishResult
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class ChatsOutboxPublisher(
    kafkaTemplate: KafkaTemplate<String, ByteArray>,
) : OutboxPublisher {

    private val delegate = KafkaTemplateOutboxPublisher(kafkaTemplate)

    override fun publish(entry: OutboxEntry): PublishResult = delegate.publish(entry)
}
