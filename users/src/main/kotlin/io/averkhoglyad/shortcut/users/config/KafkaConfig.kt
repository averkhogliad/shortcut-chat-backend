package io.averkhoglyad.shortcut.users.config

import io.averkhoglyad.shortcut.outbox.autoconfigure.KafkaTemplateOutboxPublisher
import io.averkhoglyad.shortcut.outbox.messaging.OutboxPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate

@Configuration
class KafkaConfig {

    @Bean
    fun outboxPublisher(kafkaTemplate: KafkaTemplate<String, ByteArray>): OutboxPublisher =
        KafkaTemplateOutboxPublisher(kafkaTemplate)
}