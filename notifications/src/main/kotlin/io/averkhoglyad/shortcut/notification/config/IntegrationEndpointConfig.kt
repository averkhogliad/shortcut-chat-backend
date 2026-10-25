package io.averkhoglyad.shortcut.notification.config

import io.averkhoglyad.shortcut.notification.integration.ChatMembersEndpoint
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.invoker.HttpExchangeAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient

@Configuration
class IntegrationEndpointConfig {

    @Bean
    fun httpExchangeAdapter(
        httpBuilder: WebClient.Builder,
        @Value("\${integrations.chat-service.base-url}") baseUrl: String
    ): HttpExchangeAdapter {
        val client = httpBuilder
            .baseUrl(baseUrl)
            .build()
        return WebClientAdapter.create(client)
    }

    @Bean
    fun tokenEndpoint(http: HttpExchangeAdapter): ChatMembersEndpoint {
        return HttpServiceProxyFactory.builder().exchangeAdapter(http).build()
            .createClient<ChatMembersEndpoint>()
    }
}