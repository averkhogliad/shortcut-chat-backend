package io.averkhoglyad.shortcut.chat.it

import io.averkhoglyad.shortcut.chat.TestcontainersConfiguration
import io.kotest.core.spec.style.FreeSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * The `chats` security chain: a request without a Bearer token is rejected before reaching
 * an endpoint, the actuator probe stays public, and a mock JWT gets past the chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class ChatSecurityTest(private val mvc: MockMvc) : FreeSpec({

    "GET /chats" - {
        "returns 401 without a token" {
            // when/then
            mvc.perform(get("/chats"))
                .andExpect(status().isUnauthorized)
        }

        "returns the caller's chats with a mock JWT" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

            // when/then
            mvc.perform(get("/chats").with(jwtFor(userId)))
                .andExpect(status().isOk)
        }
    }

    "GET /members" - {
        "returns 401 without a token" {
            // when/then
            mvc.perform(get("/members"))
                .andExpect(status().isUnauthorized)
        }

        "returns 401 for an invalid Bearer token" {
            // when/then
            mvc.perform(
                get("/members").header("Authorization", "Bearer not-a-jwt")
            )
                .andExpect(status().isUnauthorized)
        }

        "reaches the controller with a mock JWT" {
            // when/then
            mvc.perform(get("/members").with(jwt()))
                .andExpect(status().isOk)
        }
    }

    "GET /actuator/health" - {
        "is available without a token" {
            // when/then
            mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk)
        }
    }
})
