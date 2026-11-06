package io.averkhoglyad.shortcut.message

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey

/**
 * Replaces the auto-configured `JwtDecoder` of the test context. The configured
 * `spring.security.oauth2.resourceserver.jwt.issuer-uri` has no test default and would make
 * the decoder discover the issuer over the network; the decoder below is built from a key
 * generated locally.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestSecurityConfiguration {

    @Bean
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withPublicKey(generatedKeyPair().public as RSAPublicKey).build()

    private fun generatedKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()
}
