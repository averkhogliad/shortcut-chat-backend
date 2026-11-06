package io.averkhoglyad.shortcut.notification

import io.averkhoglyad.shortcut.notification.data.ChatEventWithMembers
import io.averkhoglyad.shortcut.notification.data.ChatLifecycleEvent
import io.averkhoglyad.shortcut.notification.service.NotificationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * Replaces the real event stream with a test-controlled one while keeping the production
 * router, security chain and handler. Tests assign [stream] to control what the subscription
 * observes.
 */
class ControlledNotificationService : NotificationService {

    var stream: Flow<List<ChatEventWithMembers>> = emptyFlow()

    override suspend fun handleEvent(event: ChatLifecycleEvent) = Unit

    override fun events(): Flow<List<ChatEventWithMembers>> = stream

    fun reset() {
        stream = emptyFlow()
    }
}

@TestConfiguration(proxyBeanMethods = false)
class NotificationStreamTestConfiguration {

    @Bean
    @Primary
    fun notificationService(): ControlledNotificationService = ControlledNotificationService()
}
