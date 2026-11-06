package io.averkhoglyad.shortcut.message.it

import io.averkhoglyad.shortcut.message.TestSecurityConfiguration
import io.averkhoglyad.shortcut.message.TestcontainersConfiguration
import io.kotest.core.spec.style.FreeSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * The security filter chain: an authenticated endpoint rejects a request without a Bearer
 * token, accepts a mock JWT, and the actuator probes stay public.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, TestSecurityConfiguration::class)
class SecurityChainTest(
    private val mvc: MockMvc,
) : FreeSpec({

    "GET /messages" - {
        "returns 401 without a token" {
            // given
            val chatId = Arb.uuid().next()

            // when
            val response = mvc.perform(
                get("/messages").queryParam("chat", chatId.toString())
            )

            // then
            response.andExpect(status().isUnauthorized)
        }

        "reaches the controller with a mock JWT" {
            // given
            val chatId = Arb.uuid().next()

            // when
            val response = mvc.perform(
                get("/messages")
                    .queryParam("chat", chatId.toString())
                    .with(jwt())
            )

            // then
            response.andExpect(status().isOk)
        }
    }

    "POST /messages" - {
        "returns 401 without a token" {
            // given
            val body = messageBody()

            // when
            val response = mvc.perform(
                post("/messages")
                    .contentType(APPLICATION_JSON)
                    .content(body)
            )

            // then
            response.andExpect(status().isUnauthorized)
        }

        "reaches the controller with a mock JWT" {
            // given
            val body = messageBody(chatId = "invalid-uuid")

            // when
            val response = mvc.perform(
                post("/messages")
                    .contentType(APPLICATION_JSON)
                    .content(body)
                    .with(jwt())
            )

            // then
            response.andExpect(status().isBadRequest)
        }
    }

    "GET /actuator/health" - {
        "is available without a token" {
            // when
            val response = mvc.perform(get("/actuator/health"))

            // then
            response.andExpect(status().isOk)
        }
    }
})

private fun messageBody(chatId: String = Arb.uuid().next().toString()): String =
    """{"text":"${Arb.string(1..100).next()}","chat":{"id":"$chatId"}}"""
