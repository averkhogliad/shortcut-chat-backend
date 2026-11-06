package io.averkhoglyad.shortcut.users.security

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.email
import io.kotest.property.arbitrary.next
import io.mockk.clearAllMocks
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames

class EmailOtpGrantAuthenticationConverterTest : FreeSpec({

    val converter = EmailOtpGrantAuthenticationConverter()
    val client = authenticatedClient()

    beforeTest {
        SecurityContextHolder.getContext().authentication = client
    }

    afterTest {
        SecurityContextHolder.clearContext()
        clearAllMocks()
    }

    "convert" - {

        "turns an email-otp request into an unauthenticated grant token" {
            // given
            val email = Arb.email().next()
            val code = "123456"
            val request = tokenRequest(
                mapOf(
                    OAuth2ParameterNames.GRANT_TYPE to arrayOf(EMAIL_OTP_GRANT_TYPE.value),
                    "email" to arrayOf(email),
                    OAuth2ParameterNames.CODE to arrayOf(code),
                )
            )

            // when
            val result = converter.convert(request).shouldBeInstanceOf<EmailOtpGrantAuthenticationToken>()

            // then
            result.email shouldBe email
            result.code shouldBe code
            result.grantType shouldBe EMAIL_OTP_GRANT_TYPE
            result.isAuthenticated shouldBe false
            result.principal shouldBeSameInstanceAs client
        }

        "returns null for any other grant type" {
            // given
            val request = tokenRequest(
                mapOf(
                    OAuth2ParameterNames.GRANT_TYPE to arrayOf("authorization_code"),
                    "email" to arrayOf(Arb.email().next()),
                    OAuth2ParameterNames.CODE to arrayOf("123456"),
                )
            )

            // when/then
            converter.convert(request) shouldBe null
        }

        "rejects a request without an email" {
            // given
            val request = tokenRequest(
                mapOf(
                    OAuth2ParameterNames.GRANT_TYPE to arrayOf(EMAIL_OTP_GRANT_TYPE.value),
                    OAuth2ParameterNames.CODE to arrayOf("123456"),
                )
            )

            // when
            val error = shouldThrow<OAuth2AuthenticationException> {
                converter.convert(request)
            }

            // then
            error.error.errorCode shouldBe OAuth2ErrorCodes.INVALID_REQUEST
        }

        "rejects a request without a client principal" {
            // given
            SecurityContextHolder.clearContext()
            val request = tokenRequest(
                mapOf(
                    OAuth2ParameterNames.GRANT_TYPE to arrayOf(EMAIL_OTP_GRANT_TYPE.value),
                    "email" to arrayOf(Arb.email().next()),
                    OAuth2ParameterNames.CODE to arrayOf("123456"),
                )
            )

            // when
            val error = shouldThrow<OAuth2AuthenticationException> {
                converter.convert(request)
            }

            // then
            error.error.errorCode shouldBe OAuth2ErrorCodes.INVALID_CLIENT
        }
    }
})
