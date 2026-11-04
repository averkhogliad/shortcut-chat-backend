package io.averkhoglyad.shortcut.notification.kafka

import io.averkhoglyad.shortcut.common.kafka.KafkaHeaders
import io.averkhoglyad.shortcut.notification.data.ChatCreated
import io.averkhoglyad.shortcut.notification.data.MessagePublished
import org.apache.kafka.common.header.Headers
import org.springframework.kafka.support.serializer.JacksonJsonTypeResolver
import org.springframework.stereotype.Component
import tools.jackson.databind.JavaType
import tools.jackson.databind.type.TypeFactory

@Component
class ChatLifecycleTypeResolver: JacksonJsonTypeResolver {

    override fun resolveType(topic: String, data: ByteArray, headers: Headers?): JavaType {
        val typeFactory = TypeFactory.createDefaultInstance()
        return when (headers?.getLastAsString(KafkaHeaders.EVENT_NAME)) {
            "ChatCreated" -> typeFactory.constructType(ChatCreated::class.java)
            "MessagePublished" -> typeFactory.constructType(MessagePublished::class.java)
            else -> typeFactory.constructMapType(Map::class.java, String::class.java, Any::class.java)
        }
    }
}

private fun Headers.getLastAsString(name: String): String {
    return lastHeader(name)?.value()?.toString(Charsets.UTF_8) ?: ""
}
