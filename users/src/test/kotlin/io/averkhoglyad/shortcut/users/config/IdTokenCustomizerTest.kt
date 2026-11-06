package io.averkhoglyad.shortcut.users.config

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext

private const val EMAIL = "user@example.com"
private const val NAME = "User Name"

class IdTokenCustomizerTest : FreeSpec({

    val customizer = AuthServerConfig().idTokenCustomizer()

    "customize" - {
        "adds email and name claims to ID tokens" {
            // given
            val context = encodingContext(OAuth2TokenType(OidcParameterNames.ID_TOKEN))

            // when
            customizer.customize(context)

            // then
            val claims = context.claims.build()
            claims.getClaim<String>("email") shouldBe EMAIL
            claims.getClaim<String>("name") shouldBe NAME
        }

        "leaves access token claims unchanged" {
            // given
            val context = encodingContext(OAuth2TokenType.ACCESS_TOKEN)

            // when
            customizer.customize(context)

            // then
            val claims = context.claims.build()
            claims.getClaim<String>("email") shouldBe null
            claims.getClaim<String>("name") shouldBe null
        }
    }
})

private val registeredClient = RegisteredClient.withId("test-registration")
    .clientId("test-client")
    .clientSecret("test-secret")
    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
    .redirectUri("http://localhost/callback")
    .scope(OidcScopes.OPENID)
    .build()

private val principal = UsernamePasswordAuthenticationToken(
    DefaultOAuth2User(
        emptyList(),
        mapOf("sub" to "00000000-0000-0000-0000-000000000001", "email" to EMAIL, "name" to NAME),
        "sub",
    ),
    null,
    emptyList(),
)

private fun encodingContext(tokenType: OAuth2TokenType): JwtEncodingContext =
    JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder().issuer("http://localhost:8080"))
        .tokenType(tokenType)
        .registeredClient(registeredClient)
        .principal(principal)
        .build()
