package io.averkhoglyad.shortcut.outbox.autoconfigure

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.string

class OutboxChannelGen(
    val names: Arb<String> = Arb.string(1..10),
    val topics: Arb<String> = Arb.string(1..10),
)

class OutboxRouteGen(
    val types: Arb<String> = Arb.string(1..10),
    val channels: Arb<String> = Arb.string(1..10),
    val eventNames: Arb<String> = Arb.string(1..10),
)

fun Arb.Companion.outboxChannels(fields: OutboxChannelGen = OutboxChannelGen()): Arb<TxOutboxProperties.Channel> =
    Arb.bind(fields.names, fields.topics) { name, topic ->
        channel(name = name, topic = topic)
    }

fun Arb.Companion.outboxRoutes(fields: OutboxRouteGen = OutboxRouteGen()): Arb<TxOutboxProperties.Route> =
    Arb.bind(fields.types, fields.channels, fields.eventNames) { type, channel, eventName ->
        route(type = type, channel = channel, eventName = eventName)
    }

fun channel(
    name: String = "user-lifecycle",
    topic: String = "UserLifecycle",
): TxOutboxProperties.Channel = TxOutboxProperties.Channel().apply {
    this.name = name
    this.topic = topic
}

fun route(
    type: String = "UserCreated",
    channel: String = "user-lifecycle",
    eventName: String = "UserCreated",
    headers: Map<String, String> = emptyMap(),
): TxOutboxProperties.Route = TxOutboxProperties.Route().apply {
    this.type = type
    this.channel = channel
    this.eventName = eventName
    this.headers = headers
}
