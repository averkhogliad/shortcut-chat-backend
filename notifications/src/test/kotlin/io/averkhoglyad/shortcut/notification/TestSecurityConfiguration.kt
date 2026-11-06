package io.averkhoglyad.shortcut.notification

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.web.reactive.function.client.WebClient
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

/**
 * Replaces the issuer-based decoders: `spring.security.oauth2.resourceserver.jwt.issuer-uri`
 * has no test default and would make them discover the issuer against `users` over the
 * network, which does not run in tests. Both decoders are built from a locally generated
 * key: the reactive one serves the security chain, the plain one keeps Boot's issuer-based
 * decoder configuration off, and the encoder signs the tokens used by security tests.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestSecurityConfiguration {

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build()

    @Bean
    fun reactiveJwtDecoder(): ReactiveJwtDecoder =
        NimbusReactiveJwtDecoder.withPublicKey(keyPair.public as RSAPublicKey).build()

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder
        .withKeyPair(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)
        .build()

    /**
     * Boot 4 keeps `WebClient` auto-configuration in a separate module that the webflux
     * starter does not pull, while `IntegrationEndpointConfig` requires the builder. The
     * test context only needs the bean to start; no request goes through it.
     */
    @Bean
    fun webClientBuilder(): WebClient.Builder = WebClient.builder()
}
