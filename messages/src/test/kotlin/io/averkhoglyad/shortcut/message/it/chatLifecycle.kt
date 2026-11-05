package io.averkhoglyad.shortcut.message.it

import io.averkhoglyad.shortcut.message.core.model.ChatCreatedPayload
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.*

internal const val CHAT_LIFECYCLE = "ChatLifecycle"

internal fun chatCreatedRecord(
    payload: ChatCreatedPayload,
    eventTime: Instant,
): ProducerRecord<String, Any> = eventRecord(
    eventName = "ChatCreated",
    key = payload.id.toString(),
    eventTime = eventTime,
    payload = payload,
    publishedBy = "chats-app",
)

internal fun eventRecord(
    eventName: String,
    key: String,
    eventTime: Instant,
    payload: Any,
    publishedBy: String,
): ProducerRecord<String, Any> {
    val headers = RecordHeaders().apply {
        add("X-Event-Id", UUID.randomUUID().toString().toByteArray(Charsets.UTF_8))
        add("X-Event-Name", eventName.toByteArray(Charsets.UTF_8))
        add("X-Event-Version", "v1".toByteArray(Charsets.UTF_8))
        add("X-Published-At", eventTime.toString().toByteArray(Charsets.UTF_8))
        add("X-Published-By", publishedBy.toByteArray(Charsets.UTF_8))
    }
    return ProducerRecord(CHAT_LIFECYCLE, null, null, key, payload, headers)
}

internal fun JdbcClient.loadChatRow(id: UUID): Map<String, Any?>? {
    return this.sql("select * from chats where id=:id")
        .param("id", id)
        .query()
        .listOfRows()
        .firstOrNull()
}

internal fun JdbcClient.countChatMembers(chatId: UUID): Long {
    return this.sql("select count(*) from chat_members where chat_id=:chatId")
        .param("chatId", chatId)
        .query(Long::class.java)
        .single()
}
