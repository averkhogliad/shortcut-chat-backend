package io.averkhoglyad.shortcut.users.api.controller

import io.averkhoglyad.shortcut.users.api.handler.EndpointExceptionAdvice
import io.averkhoglyad.shortcut.users.core.service.OtpRequestResult
import io.averkhoglyad.shortcut.users.core.service.OtpRequestService
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.email
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private fun MockMvc.requestCode(body: String) =
    perform(
        post("/auth/code")
            .contentType(APPLICATION_JSON)
            .content(body)
    )

class AuthControllerTest : FreeSpec({

    val service = mockk<OtpRequestService>()
    val mvc = MockMvcBuilders
        .standaloneSetup(AuthController(service))
        .setControllerAdvice(EndpointExceptionAdvice())
        .build()

    afterTest {
        clearAllMocks()
    }

    "POST /auth/code" - {

        "returns 202 with an empty body and requests the code for the given email" {
            // given
            val email = Arb.email().next()
            val code = Arb.string(6).next()
            every { service.requestCode(any()) } returns OtpRequestResult.CodeSent(code)

            // when
            mvc.requestCode("""{"email":"$email"}""")
                .andExpect(status().isAccepted)
                .andExpect(content().string(""))

            // then
            val requestedEmail = slot<String>()
            verify(exactly = 1) { service.requestCode(capture(requestedEmail)) }
            requestedEmail.captured shouldBe email
        }

        "returns the same empty 202 response for every email" {
            // given
            val emails = List(2) { Arb.email().next() }
            every { service.requestCode(any()) } returns OtpRequestResult.CodeSent("000000")

            // when
            val responses = emails.map { email ->
                mvc.requestCode("""{"email":"$email"}""")
                    .andExpect(status().isAccepted)
                    .andReturn()
                    .response
            }

            // then
            responses.map { it.status } shouldBe listOf(202, 202)
            responses.map { it.contentAsString } shouldBe listOf("", "")
        }

        "returns 429 with a ProblemDetail and no body on a rate limited request" {
            // given
            val email = Arb.email().next()
            every { service.requestCode(any()) } returns OtpRequestResult.RateLimited

            // when/then
            mvc.requestCode("""{"email":"$email"}""")
                .andExpect(status().isTooManyRequests)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("too.many.requests"))
                .andExpect(jsonPath("$.detail").value("otp.request.rate.limited"))
        }

        "returns 400 with a ProblemDetail and requests no code when the email is blank" {
            // given
            val blankEmails = listOf("", " ", "   ")

            // when/then
            blankEmails.forEach { email ->
                mvc.requestCode("""{"email":"$email"}""")
                    .andExpect(status().isBadRequest)
                    .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
            }

            verify(exactly = 0) { service.requestCode(any()) }
        }

        "returns 400 with a ProblemDetail and requests no code when the email is absent" {
            // when/then
            mvc.requestCode("""{}""")
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

            verify(exactly = 0) { service.requestCode(any()) }
        }
    }
})
