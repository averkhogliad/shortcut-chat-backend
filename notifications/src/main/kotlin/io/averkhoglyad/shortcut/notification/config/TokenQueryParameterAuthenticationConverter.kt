package io.averkhoglyad.shortcut.notification.config

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * Resolves a bearer token for the SSE subscription from the `token` query parameter when
 * the standard `Authorization` header is absent. The header keeps priority and is handled
 * by the standard converter, so ordinary requests are unaffected.
 */
class TokenQueryParameterAuthenticationConverter(
    private val delegate: ServerAuthenticationConverter = ServerBearerTokenAuthenticationConverter(),
) : ServerAuthenticationConverter {

    override fun convert(exchange: ServerWebExchange): Mono<Authentication> =
        delegate.convert(exchange)
            .switchIfEmpty(Mono.defer { tokenFromQueryParameter(exchange) })

    private fun tokenFromQueryParameter(exchange: ServerWebExchange): Mono<Authentication> =
        exchange.request.queryParams.getFirst(TOKEN_PARAMETER)
            ?.takeIf(String::isNotBlank)
            ?.let { token -> Mono.just(BearerTokenAuthenticationToken(token) as Authentication) }
            ?: Mono.empty()

    private companion object {
        const val TOKEN_PARAMETER = "token"
    }
}
