package io.averkhoglyad.shortcut.notification.config

import io.averkhoglyad.shortcut.notification.integration.ChatMembersEndpoint
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.support.WebClientAdapter
import org.springframework.web.service.invoker.HttpExchangeAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient
import reactor.core.publisher.Mono

private const val USERS_SERVICE_REGISTRATION_ID = "users-service"

/**
 * The service principal is fixed: the outbound token is a `client_credentials` service token,
 * not the token of a subscriber whose SSE stream happens to trigger the call. A stable
 * principal also makes the authorized-client cache hold one token per service instead of one
 * per subscriber.
 */
private val SERVICE_PRINCIPAL: Authentication =
    UsernamePasswordAuthenticationToken("notifications-service", null, emptyList<GrantedAuthority>())

@Configuration
class IntegrationEndpointConfig {

    /**
     * Operates outside a `ServerWebExchange` (the members lookup runs from a background flow),
     * so the authorized client is stored in the `ReactiveOAuth2AuthorizedClientService`, not
     * in a request-scoped repository.
     */
    @Bean
    fun chatServiceAuthorizedClientManager(
        clientRegistrationRepository: ReactiveClientRegistrationRepository,
        authorizedClientService: ReactiveOAuth2AuthorizedClientService,
    ): ReactiveOAuth2AuthorizedClientManager =
        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
            clientRegistrationRepository,
            authorizedClientService,
        )

    @Bean
    fun chatServiceOAuth2Filter(
        authorizedClientManager: ReactiveOAuth2AuthorizedClientManager,
    ): ExchangeFilterFunction {
        val filter = ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager)
        filter.setDefaultClientRegistrationId(USERS_SERVICE_REGISTRATION_ID)
        filter.setPrincipalResolver(
            ServerOAuth2AuthorizedClientExchangeFilterFunction.PrincipalResolver {
                Mono.just(SERVICE_PRINCIPAL)
            }
        )
        return filter
    }

    @Bean
    fun chatServiceWebClient(
        httpBuilder: WebClient.Builder,
        @Value("\${integrations.chat-service.base-url}") baseUrl: String,
        chatServiceOAuth2Filter: ExchangeFilterFunction,
    ): WebClient =
        httpBuilder
            .baseUrl(baseUrl)
            .filter(chatServiceOAuth2Filter)
            .build()

    @Bean
    fun httpExchangeAdapter(chatServiceWebClient: WebClient): HttpExchangeAdapter =
        WebClientAdapter.create(chatServiceWebClient)

    @Bean
    fun tokenEndpoint(http: HttpExchangeAdapter): ChatMembersEndpoint {
        return HttpServiceProxyFactory.builder().exchangeAdapter(http).build()
            .createClient<ChatMembersEndpoint>()
    }
}
