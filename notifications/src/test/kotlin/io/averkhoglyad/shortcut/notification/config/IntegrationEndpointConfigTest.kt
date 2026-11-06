package io.averkhoglyad.shortcut.notification.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthorizationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID.randomUUID

class IntegrationEndpointConfigTest : FreeSpec({

    val config = IntegrationEndpointConfig()
    val manager = mockk<ReactiveOAuth2AuthorizedClientManager>()

    afterTest {
        clearAllMocks()
    }

    "chatServiceWebClient" - {
        "adds the service client Bearer token to the outbound members request" {
            // given
            val clientRegistration = clientRegistration()
            val authorizedClient = OAuth2AuthorizedClient(
                clientRegistration,
                "notifications-service",
                OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    "service-access-token",
                    Instant.now(),
                    Instant.now().plusSeconds(300),
                ),
            )
            every { manager.authorize(any()) } returns Mono.just(authorizedClient)
            var captured: ClientRequest? = null
            val builder = WebClient.builder()
                .exchangeFunction { request ->
                    captured = request
                    Mono.just(
                        ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("[]")
                            .build()
                    )
                }
            val client = config.chatServiceWebClient(
                builder,
                "http://chats",
                config.chatServiceOAuth2Filter(manager),
            )

            // when
            client.get()
                .uri { builder -> builder.path("/members").queryParam("chatId", randomUUID()).build() }
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

            // then
            val request = requireNotNull(captured)
            request.url().path shouldBe "/members"
            request.headers().getFirst(HttpHeaders.AUTHORIZATION) shouldBe "Bearer service-access-token"
        }

        "does not send the request when the service token cannot be obtained" {
            // given
            every { manager.authorize(any()) } returns
                Mono.error(OAuth2AuthorizationException(OAuth2Error("invalid_client")))
            var exchanged = false
            val builder = WebClient.builder()
                .exchangeFunction {
                    exchanged = true
                    Mono.just(ClientResponse.create(HttpStatus.OK).build())
                }
            val client = config.chatServiceWebClient(
                builder,
                "http://chats",
                config.chatServiceOAuth2Filter(manager),
            )

            // when
            shouldThrow<OAuth2AuthorizationException> {
                client.get()
                    .uri { builder -> builder.path("/members").queryParam("chatId", randomUUID()).build() }
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .block()
            }

            // then
            exchanged shouldBe false
        }
    }
})

private fun clientRegistration(): ClientRegistration =
    ClientRegistration.withRegistrationId("users-service")
        .clientId("test-service-client")
        .clientSecret("test-service-client-secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .tokenUri("http://localhost:8080/oauth2/token")
        .build()
