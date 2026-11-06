package io.averkhoglyad.shortcut.notification.config

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import org.springframework.http.HttpHeaders
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken

class TokenQueryParameterAuthenticationConverterTest : FreeSpec({

    val target = TokenQueryParameterAuthenticationConverter()

    "convert" - {
        "takes the token from the token query parameter" {
            // given
            val token = Arb.string(minSize = 1, maxSize = 100, codepoints = Codepoint.alphanumeric()).next()
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/subscribe").queryParam("token", token).build()
            )

            // when
            val authentication = target.convert(exchange).block()

            // then
            val bearerToken = authentication.shouldBeInstanceOf<BearerTokenAuthenticationToken>()
            bearerToken.token shouldBe token
        }

        "keeps the Authorization header as the source of the token" {
            // given
            val token = Arb.string(minSize = 1, maxSize = 100, codepoints = Codepoint.alphanumeric()).next()
            val queryToken = Arb.string(minSize = 1, maxSize = 100, codepoints = Codepoint.alphanumeric()).next()
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/subscribe")
                    .queryParam("token", queryToken)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                    .build()
            )

            // when
            val authentication = target.convert(exchange).block()

            // then
            val bearerToken = authentication.shouldBeInstanceOf<BearerTokenAuthenticationToken>()
            bearerToken.token shouldBe token
        }

        "returns an empty result when no token is present" {
            // given
            val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/subscribe").build())

            // when
            val authentication = target.convert(exchange)

            // then
            authentication.block() shouldBe null
        }
    }
})
