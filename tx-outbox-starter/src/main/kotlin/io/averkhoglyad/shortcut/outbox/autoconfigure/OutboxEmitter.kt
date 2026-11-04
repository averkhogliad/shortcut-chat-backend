package io.averkhoglyad.shortcut.outbox.autoconfigure

import tools.jackson.databind.ObjectMapper
import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator
import io.averkhoglyad.shortcut.outbox.core.OutboxEntry
import io.averkhoglyad.shortcut.outbox.core.OutboxWriter
import java.time.Clock

interface OutboxEmitter {

    fun emit(type: String, version: String, key: String?, bodyPayload: Any)

}

class OutboxEmitterImpl(
    private val router: OutboxRouter,
    private val headerProvider: OutboxHeaderProvider,
    private val writer: OutboxWriter,
    private val objectMapper: ObjectMapper,
    private val applicationName: String,
    private val clock: Clock = Clock.systemUTC(),
    private val uuidGenerator: TimeBasedEpochGenerator = Generators.timeBasedEpochGenerator(),
) : OutboxEmitter {

    override fun emit(type: String, version: String, key: String?, bodyPayload: Any) {
        val route = router.route(type)
        val id = uuidGenerator.generate()
        val now = clock.instant()
        val headers = headerProvider.headersFor(type, version, route, id, now, applicationName)
        val body = objectMapper.writeValueAsString(bodyPayload)

        writer.write(
            OutboxEntry(
                topic = route.topic,
                key = key,
                body = body,
                headers = headers,
                id = id,
                createdAt = now,
            )
        )
    }
}
