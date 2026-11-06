package io.averkhoglyad.shortcut.users.security

import io.averkhoglyad.shortcut.users.TestcontainersConfiguration
import io.averkhoglyad.shortcut.users.core.service.OtpRequestResult
import io.averkhoglyad.shortcut.users.core.service.OtpService
import io.averkhoglyad.shortcut.users.core.service.OtpVerificationResult
import io.kotest.core.spec.style.FreeSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.email
import io.kotest.property.arbitrary.next
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Proves the token endpoint is wired with the custom converter and provider: a valid
 * client request with the email-otp grant reaches the provider, so the response is
 * `invalid_grant` instead of `unsupported_grant_type`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, FixedOtpConfiguration::class)
class EmailOtpGrantWiringTest(
    private val mvc: MockMvc,
) : FreeSpec({

    "POST /oauth2/token" - {

        "recognizes the email-otp grant and routes it to the provider" {
            // given
            val email = Arb.email().next()

            // when/then
            mvc.perform(
                post("/oauth2/token")
                    .header(HttpHeaders.AUTHORIZATION, basicAuth(TEST_CLIENT_ID, TEST_CLIENT_SECRET))
                    .param(OAuth2ParameterNames.GRANT_TYPE, EMAIL_OTP_GRANT_TYPE.value)
                    .param("email", email)
                    .param(OAuth2ParameterNames.CODE, "123456")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value(OAuth2ErrorCodes.INVALID_GRANT))
        }
    }
})

private fun basicAuth(clientId: String, secret: String): String {
    val value = "$clientId:$secret".toByteArray(StandardCharsets.UTF_8)
    return "Basic " + Base64.getEncoder().encodeToString(value)
}

@TestConfiguration(proxyBeanMethods = false)
class FixedOtpConfiguration {

    @Bean
    @Primary
    fun fixedOtpService(): OtpService = object : OtpService {

        override fun requestCode(email: String): OtpRequestResult =
            OtpRequestResult.RateLimited

        override fun verifyCode(email: String, code: String): OtpVerificationResult =
            OtpVerificationResult.INVALID

    }

}
