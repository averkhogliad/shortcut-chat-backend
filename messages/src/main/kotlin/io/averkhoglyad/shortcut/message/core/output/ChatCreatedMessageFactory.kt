package io.averkhoglyad.shortcut.message.core.output

import io.averkhoglyad.shortcut.common.kafka.KafkaHeaders
import io.averkhoglyad.shortcut.message.core.model.Message
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Headers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class MessageCreatedMessageFactoryImpl(
    @Value("ChatLifecycle")
    private val topic: String,
    @Value("v1")
    private val version: String,
    private val eventName: String,
    @Value("\${spring.application.name}")
    private val applicationName: String,
    private val clock: Clock,
) : ProducerRecordFactory<Message, ProducerRecord<String, Any>> {

    @Autowired
    constructor(
        @Value("ChatLifecycle") topic: String,
        @Value("MessagePublished") eventName: String,
        @Value("v1") version: String,
        @Value("\${spring.application.name}") applicationName: String,
    ) : this(topic, version, eventName, applicationName, Clock.systemUTC())


    override fun create(event: Message): ProducerRecord<String, Any> =
        ProducerRecord<String, Any>(topic, event.chat.id.toString(), event)
            .apply {
                headers()
                    .addAsString(KafkaHeaders.EVENT_ID, event.id.toString())
                    .addAsString(KafkaHeaders.EVENT_NAME, eventName)
                    .addAsString(KafkaHeaders.EVENT_VERSION, version)
                    .addAsString(KafkaHeaders.PUBLISHED_AT, clock.instant())
                    .addAsString(KafkaHeaders.PUBLISHED_BY, applicationName)
            }
}

private fun Headers.addAsString(name: String, value: String): Headers =
    add(name, value.toByteArray(Charsets.UTF_8))

private fun Headers.addAsString(name: String, value: Any): Headers =
    addAsString(name, value.toString())
