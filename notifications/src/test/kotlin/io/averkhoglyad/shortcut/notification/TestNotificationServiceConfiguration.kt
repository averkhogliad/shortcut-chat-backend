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
 * The real subscription stream never completes, so an SSE request would wait for the first
 * event and never let the security test read the status. An empty, completed stream keeps
 * the transport behaviour of the endpoint while making the response observable; the
 * security chain is what this test verifies, not the event feed.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestNotificationServiceConfiguration {

    @Bean
    @Primary
    fun notificationService(): NotificationService = object : NotificationService {

        override suspend fun handleEvent(event: ChatLifecycleEvent) = Unit

        override fun events(): Flow<List<ChatEventWithMembers>> = emptyFlow()
    }
}
