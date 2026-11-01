package io.averkhoglyad.shortcut.outbox.core

interface OutboxWriter {

    fun write(entry: OutboxEntry)

}
