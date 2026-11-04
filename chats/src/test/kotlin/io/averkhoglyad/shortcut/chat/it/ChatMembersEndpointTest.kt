package io.averkhoglyad.shortcut.chat.it

import io.averkhoglyad.shortcut.chat.TestcontainersConfiguration
import io.averkhoglyad.shortcut.common.test.executeSql
import io.kotest.core.spec.style.FreeSpec
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val INIT = "/it/members/init.sql"
private const val CLEAR = "/it/members/clear.sql"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class ChatMembersEndpointTest(
    private val mvc: MockMvc,
): FreeSpec({

    beforeEach { executeSql(INIT) }
    afterEach { executeSql(CLEAR) }

    "GET /members" - {
        "returns members for single chat" {
            val chatId = "00000000-0000-0000-0000-000000000001"

            mvc.perform(get("/members").queryParam("chatId", chatId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(chatId))
                .andExpect(jsonPath("$[0].members.length()").value(2))
                .andExpect(jsonPath("$[0].members[0].id").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$[0].members[1].id").value("00000000-0000-0000-0000-000000000002"))
        }

        "returns members for multiple chats" {
            val chatId1 = "00000000-0000-0000-0000-000000000001"
            val chatId2 = "00000000-0000-0000-0000-000000000002"

            mvc.perform(get("/members").queryParam("chatId", chatId1, chatId2))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(chatId1))
                .andExpect(jsonPath("$[0].members.length()").value(2))
                .andExpect(jsonPath("$[1].id").value(chatId2))
                .andExpect(jsonPath("$[1].members.length()").value(1))
        }

        "returns empty collection for non-existent chat" {
            val nonExistentChatId = "00000000-0000-0000-0000-000000000999"

            mvc.perform(get("/members").queryParam("chatId", nonExistentChatId))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        "returns empty collection when no chatIds provided" {
            mvc.perform(get("/members"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(0))
        }

        "returns 400 for invalid chatId format" {
            mvc.perform(get("/members").queryParam("chatId", "invalid-uuid"))
                .andExpect(status().isBadRequest)
        }
    }
})