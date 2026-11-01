package io.averkhoglyad.shortcut.outbox.messaging

import io.averkhoglyad.shortcut.outbox.core.OutboxEntry

interface OutboxPublisher {

    fun publish(entry: OutboxEntry): PublishResult

}
