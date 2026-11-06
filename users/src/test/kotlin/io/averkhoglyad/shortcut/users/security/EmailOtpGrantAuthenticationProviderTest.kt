package io.averkhoglyad.shortcut.users.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import io.averkhoglyad.shortcut.common.data.EntityResult
import io.averkhoglyad.shortcut.users.config.AuthServerConfig
import io.averkhoglyad.shortcut.users.config.generateRsaKey
import io.averkhoglyad.shortcut.users.core.model.User
import io.averkhoglyad.shortcut.users.core.service.OtpService
import io.averkhoglyad.shortcut.users.core.service.OtpVerificationResult
import io.averkhoglyad.shortcut.users.core.service.UserService
import io.averkhoglyad.shortcut.users.test.gen.users
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.email
import io.kotest.property.arbitrary.next
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator

private const val ISSUER = "http://localhost:8080"
private const val CODE = "123456"

class EmailOtpGrantAuthenticationProviderTest : FreeSpec({

    val otpService = mockk<OtpService>()
    val userService = mockk<UserService>()
    val authorizationService = mockk<OAuth2AuthorizationService>()
    val registeredClient = testRegisteredClient()

    val jwk = generateRsaKey()
    val jwtGenerator = JwtGenerator(NimbusJwtEncoder(ImmutableJWKSet<SecurityContext>(JWKSet(jwk))))
        .apply { setJwtCustomizer(AuthServerConfig().idTokenCustomizer()) }
    val tokenGenerator = DelegatingOAuth2TokenGenerator(jwtGenerator, OAuth2RefreshTokenGenerator())
    val provider = EmailOtpGrantAuthenticationProvider(
        otpService = otpService,
        userService = userService,
        authorizationService = authorizationService,
        tokenGenerator = tokenGenerator,
    )
    val serverContext = object : AuthorizationServerContext {
        override fun getIssuer(): String = ISSUER

        override fun getAuthorizationServerSettings(): AuthorizationServerSettings =
            AuthorizationServerSettings.builder().issuer(ISSUER).build()
    }

    beforeTest {
        AuthorizationServerContextHolder.setContext(serverContext)
        every { authorizationService.save(any()) } just runs
    }

    afterTest {
        AuthorizationServerContextHolder.resetContext()
        clearAllMocks()
    }

    "authenticate" - {

        "issues access, ID and refresh tokens with the existing user id as subject" {
            // given
            val email = Arb.email().next()
            val existing = users.next()
            every { otpService.verifyCode(any(), any()) } returns OtpVerificationResult.SUCCESS
            every { userService.findByEmail(any()) } returns EntityResult.Success(existing)

            // when
            val result = provider.authenticate(grant(email))
                .shouldBeInstanceOf<OAuth2AccessTokenAuthenticationToken>()

            // then
            result.accessToken.tokenType shouldBe OAuth2AccessToken.TokenType.BEARER
            result.accessToken.scopes shouldBe registeredClient.scopes
            result.refreshToken.shouldNotBeNull()
            result.additionalParameters[OidcParameterNames.ID_TOKEN].shouldNotBeNull()

            val saved = slot<OAuth2Authorization>()
            verify(exactly = 1) { authorizationService.save(capture(saved)) }
            val accessClaims = saved.captured.getAccessToken().shouldNotBeNull().claims.shouldNotBeNull()
            accessClaims["sub"] shouldBe existing.id.toString()
            accessClaims["iss"].toString() shouldBe ISSUER
            accessClaims["scope"] shouldBe registeredClient.scopes

            val idClaims = saved.captured.getToken(OidcIdToken::class.java)
                .shouldNotBeNull()
                .claims
                .shouldNotBeNull()
            idClaims["sub"] shouldBe existing.id.toString()
            idClaims["email"] shouldBe existing.email
            idClaims["name"] shouldBe existing.name
            saved.captured.getRefreshToken().shouldNotBeNull()

            val emailSlot = slot<String>()
            val codeSlot = slot<String>()
            verify(exactly = 1) { otpService.verifyCode(capture(emailSlot), capture(codeSlot)) }
            emailSlot.captured shouldBe email
            codeSlot.captured shouldBe CODE
            verify(exactly = 0) { userService.create(any()) }
        }

        "registers an unknown email once with its local part as the name" {
            // given
            val email = Arb.email().next()
            val created = users.next()
            every { otpService.verifyCode(any(), any()) } returns OtpVerificationResult.SUCCESS
            every { userService.findByEmail(any()) } returns EntityResult.NotFound
            every { userService.create(any()) } returns EntityResult.Success(created)

            // when
            val result = provider.authenticate(grant(email))
                .shouldBeInstanceOf<OAuth2AccessTokenAuthenticationToken>()

            // then
            val candidate = slot<User>()
            verify(exactly = 1) { userService.create(capture(candidate)) }
            candidate.captured.id shouldBe null
            candidate.captured.name shouldBe email.substringBefore("@")
            candidate.captured.email shouldBe email

            val saved = slot<OAuth2Authorization>()
            verify(exactly = 1) { authorizationService.save(capture(saved)) }
            saved.captured.getAccessToken().shouldNotBeNull()
                .claims.shouldNotBeNull()["sub"] shouldBe created.id.toString()
            result.accessToken.tokenValue.shouldNotBeNull()
        }

        "reuses the user created by a parallel first login when the insert loses the race" {
            // given
            val email = Arb.email().next()
            val existing = users.next()
            every { otpService.verifyCode(any(), any()) } returns OtpVerificationResult.SUCCESS
            every { userService.findByEmail(any()) } returnsMany listOf(
                EntityResult.NotFound,
                EntityResult.Success(existing),
            )
            every { userService.create(any()) } throws DuplicateKeyException("duplicate email")

            // when
            provider.authenticate(grant(email))

            // then
            val candidate = slot<User>()
            verify(exactly = 1) { userService.create(capture(candidate)) }
            candidate.captured.name shouldBe email.substringBefore("@")
            candidate.captured.email shouldBe email

            val saved = slot<OAuth2Authorization>()
            verify(exactly = 1) { authorizationService.save(capture(saved)) }
            saved.captured.getAccessToken().shouldNotBeNull()
                .claims.shouldNotBeNull()["sub"] shouldBe existing.id.toString()
        }

        "rejects an incorrect code without touching the user store" {
            // given
            val email = Arb.email().next()
            every { otpService.verifyCode(any(), any()) } returns OtpVerificationResult.INVALID

            // when
            val error = shouldThrow<OAuth2AuthenticationException> {
                provider.authenticate(grant(email))
            }

            // then
            error.error.errorCode shouldBe OAuth2ErrorCodes.INVALID_GRANT

            val emailSlot = slot<String>()
            val codeSlot = slot<String>()
            verify(exactly = 1) { otpService.verifyCode(capture(emailSlot), capture(codeSlot)) }
            emailSlot.captured shouldBe email
            codeSlot.captured shouldBe CODE
            verify(exactly = 0) { userService.findByEmail(any()) }
            verify(exactly = 0) { userService.create(any()) }
            verify(exactly = 0) { authorizationService.save(any()) }
        }

        "rejects an exhausted or expired code without creating a user" {
            // given
            val email = Arb.email().next()
            val results = listOf(OtpVerificationResult.EXHAUSTED, OtpVerificationResult.EXPIRED)

            // when/then
            results.forEach { verificationResult ->
                every { otpService.verifyCode(any(), any()) } returns verificationResult
                val error = shouldThrow<OAuth2AuthenticationException> {
                    provider.authenticate(grant(email))
                }
                error.error.errorCode shouldBe OAuth2ErrorCodes.INVALID_GRANT
            }

            verify(exactly = 0) { userService.findByEmail(any()) }
            verify(exactly = 0) { userService.create(any()) }
            verify(exactly = 0) { authorizationService.save(any()) }
        }

        "rejects a client that is not registered for the email-otp grant" {
            // given
            val clientWithoutGrant = authenticatedClient(testRegisteredClient(includeEmailOtpGrant = false))
            val request = EmailOtpGrantAuthenticationToken(
                email = Arb.email().next(),
                code = CODE,
                clientPrincipal = clientWithoutGrant,
            )

            // when
            val error = shouldThrow<OAuth2AuthenticationException> {
                provider.authenticate(request)
            }

            // then
            error.error.errorCode shouldBe OAuth2ErrorCodes.UNAUTHORIZED_CLIENT
            verify(exactly = 0) { otpService.verifyCode(any(), any()) }
        }
    }
})

private fun grant(email: String): EmailOtpGrantAuthenticationToken =
    EmailOtpGrantAuthenticationToken(
        email = email,
        code = CODE,
        clientPrincipal = authenticatedClient(),
    )
