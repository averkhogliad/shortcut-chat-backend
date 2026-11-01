package io.averkhoglyad.shortcut.outbox.messaging

sealed interface PublishResult {

    data object Success : PublishResult

    data class Failure(val error: Throwable? = null) : PublishResult

}
