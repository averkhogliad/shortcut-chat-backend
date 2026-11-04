package io.averkhoglyad.shortcut.chat.kafka

import io.averkhoglyad.shortcut.chat.core.model.UserCreatedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserDeletedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserUpdatedPayload
import org.apache.kafka.common.header.Headers
import org.springframework.kafka.support.serializer.JacksonJsonTypeResolver
import org.springframework.stereotype.Component
import tools.jackson.databind.JavaType
import tools.jackson.databind.type.TypeFactory

private const val EVENT_NAME = "X-Event-Name"

@Component
class UserLifecycleTypeResolver : JacksonJsonTypeResolver {

    override fun resolveType(topic: String, data: ByteArray, headers: Headers?): JavaType {
        val typeFactory = TypeFactory.createDefaultInstance()
        return when (headers?.getLastAsString(EVENT_NAME)) {
            "UserCreated" -> typeFactory.constructType(UserCreatedPayload::class.java)
            "UserUpdated" -> typeFactory.constructType(UserUpdatedPayload::class.java)
            "UserDeleted" -> typeFactory.constructType(UserDeletedPayload::class.java)
            else -> typeFactory.constructMapType(Map::class.java, String::class.java, Any::class.java)
        }
    }
}

private fun Headers.getLastAsString(name: String): String {
    return lastHeader(name)?.value()?.toString(Charsets.UTF_8) ?: ""
}
