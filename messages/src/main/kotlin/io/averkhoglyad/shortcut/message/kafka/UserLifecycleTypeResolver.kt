package io.averkhoglyad.shortcut.message.kafka

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.type.TypeFactory
import io.averkhoglyad.shortcut.message.core.model.UserCreatedPayload
import io.averkhoglyad.shortcut.message.core.model.UserDeletedPayload
import io.averkhoglyad.shortcut.message.core.model.UserUpdatedPayload
import org.apache.kafka.common.header.Headers
import org.springframework.kafka.support.serializer.JsonTypeResolver
import org.springframework.stereotype.Component

private const val EVENT_NAME = "X-Event-Name"

@Component
class UserLifecycleTypeResolver : JsonTypeResolver {

    override fun resolveType(topic: String, data: ByteArray?, headers: Headers): JavaType {
        val typeFactory = TypeFactory.defaultInstance()
        return when (headers.getLastAsString(EVENT_NAME)) {
            "UserCreated" -> typeFactory.constructType(UserCreatedPayload::class.java)
            "UserUpdated" -> typeFactory.constructType(UserUpdatedPayload::class.java)
            "UserDeleted" -> typeFactory.constructType(UserDeletedPayload::class.java)
            else -> typeFactory.constructMapType(Map::class.java, String::class.java, Object::class.java)
        }
    }
}

private fun Headers.getLastAsString(name: String): String {
    return lastHeader(name)?.value()?.toString(Charsets.UTF_8) ?: ""
}
