package io.averkhoglyad.shortcut.common.kafka

import org.apache.kafka.common.header.Headers
import org.springframework.kafka.support.serializer.JacksonJsonTypeResolver
import tools.jackson.databind.JavaType
import tools.jackson.databind.type.TypeFactory

abstract class EventNameTypeResolver(
    private val eventNameHeader: String,
    private val types: Map<String, Class<*>>,
) : JacksonJsonTypeResolver {

    override fun resolveType(topic: String, data: ByteArray, headers: Headers?): JavaType {
        val typeFactory = TypeFactory.createDefaultInstance()
        val eventName = headers?.getLastAsString(eventNameHeader) ?: ""
        return types[eventName]
            ?.let { typeFactory.constructType(it) }
            ?: typeFactory.constructMapType(Map::class.java, String::class.java, Any::class.java)
    }
}

private fun Headers.getLastAsString(name: String): String {
    return lastHeader(name)?.value()?.toString(Charsets.UTF_8) ?: ""
}
