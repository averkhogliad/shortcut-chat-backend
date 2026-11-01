package io.averkhoglyad.shortcut.outbox.autoconfigure

data class Route(
    val type: String,
    val channel: String,
    val topic: String,
    val eventName: String,
    val headers: Map<String, String> = emptyMap(),
)

interface OutboxRouter {

    fun route(type: String): Route

}

class ConfigurationBackedOutboxRouter(
    private val routesByType: Map<String, Route>,
) : OutboxRouter {

    constructor(properties: TxOutboxProperties) : this(
        buildRoutes(properties.channels, properties.routes)
    )

    constructor(
        channels: List<TxOutboxProperties.Channel>,
        routes: List<TxOutboxProperties.Route>,
    ) : this(buildRoutes(channels, routes))

    override fun route(type: String): Route =
        routesByType[type] ?: error("No outbox route configured for message type '$type'")

    companion object {

        fun buildRoutes(
            channels: List<TxOutboxProperties.Channel>,
            routes: List<TxOutboxProperties.Route>,
        ): Map<String, Route> {
            val topicByChannel = channels.associate { it.name to it.topic }
            return routes.associate { route ->
                val topic = topicByChannel[route.channel]
                    ?: error(
                        "Outbox route '${route.type}' references undeclared channel '${route.channel}'"
                    )
                route.type to Route(
                    type = route.type,
                    channel = route.channel,
                    topic = topic,
                    eventName = route.eventName,
                    headers = route.headers,
                )
            }
        }
    }
}
