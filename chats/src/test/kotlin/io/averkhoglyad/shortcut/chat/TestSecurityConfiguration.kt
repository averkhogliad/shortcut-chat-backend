package io.averkhoglyad.shortcut.chat

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

/**
 * Test replacement for the issuer-based decoder: `issuer-uri` would make
 * `JwtDecoder` resolution perform eager OIDC discovery against `users`, which does not run
 * in tests. Signature verification is irrelevant here because requests are authenticated
 * with a mock JWT.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestSecurityConfiguration {

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val key = RSAKeyGenerator(2048).keyID("test").generate()
        return NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build()
    }
}
