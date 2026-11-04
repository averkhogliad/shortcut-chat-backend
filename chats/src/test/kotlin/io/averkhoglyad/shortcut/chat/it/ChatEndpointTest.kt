package io.averkhoglyad.shortcut.chat.it

import tools.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.chat.TestcontainersConfiguration
import io.averkhoglyad.shortcut.chat.core.model.ChatDetails
import io.averkhoglyad.shortcut.chat.core.model.ChatRequest
import io.averkhoglyad.shortcut.chat.core.model.UserRef
import io.averkhoglyad.shortcut.common.test.executeSql
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldContainKeys
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*
import kotlin.reflect.KClass

private const val INIT = "/it/chat/init.sql"
private const val CLEAR = "/it/chat/clear.sql"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class ChatEndpointTest(
    private val mvc: MockMvc,
    private val jdbc: JdbcClient,
    private val mapper: ObjectMapper,
): FreeSpec({

    beforeEach { executeSql(INIT) }
    afterEach { executeSql(CLEAR) }

    "GET /chats " - {
        "returns first page if no lastId" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

            // when
            val response = mvc.perform(
                get("/chats")
                    .header("X-User-Id", userId.toString())
                    .queryParam("pageSize", "2"))

            // then
            response
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.data[0].name").value("First Chat"))
                .andExpect(jsonPath("$.next").exists())
        }

        "returns next page after lastId" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val lastId = UUID.fromString("00000000-0000-0000-0000-000000000002")

            // when
            mvc.perform(get("/chats")
                    .header("X-User-Id", userId)
                    .queryParam("pageSize", "2")
                    .queryParam("lastId", lastId.toString()))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("00000000-0000-0000-0000-000000000003"))
        }
    }

    "GET /chats/{id}" - {
        "returns chat details" {
            // given
            val chatId = UUID.fromString("00000000-0000-0000-0000-000000000001")

            // when
            mvc.perform(get("/chats/$chatId"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(chatId.toString()))
                .andExpect(jsonPath("$.name").value("First Chat"))
                .andExpect(jsonPath("$.members.length()").value(2))
        }

        "returns 404 for non-existent chat" {
            // given
            val chatId = UUID.fromString("00000000-0000-0000-0000-000000000999")

            // when
            mvc.perform(get("/chats/$chatId"))
                .andExpect(status().isNotFound)
        }
    }

    "POST /chats" - {
        "creates new chat and return details" {
            // given
            val request = ChatRequest(
                name = "New Chat",
                members = listOf(
                    UserRef(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                    UserRef(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                )
            )

            // when
            val result = mvc.perform(post("/chats")
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("New Chat"))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andReturn()

            val response = mapper.readValue(result.response.contentAsString, ChatDetails::class.java)

            // then
            val chatRow = jdbc.loadChatRow(response.id)
            withClue("Chat row") {
                chatRow shouldNotBeNull {
                    this shouldContain ("name" to request.name)
                    this.shouldContainKeys("created_at")
                }
            }

            val memberRows = jdbc.loadChatMemberRows(response.id)

            withClue("Member rows") {
                memberRows shouldHaveSize request.members.size
                val persistedMemberIds = memberRows.map { it["user_id"] }
                val expectedMemberIds = request.members.map { it.id }
                persistedMemberIds shouldContainExactlyInAnyOrder expectedMemberIds
            }

            // Проверка записи в outbox
            val outboxCount = jdbc.countTxOutbox(response.id)
            outboxCount shouldBe 1L
        }
    }
})

private fun JdbcClient.loadChatRow(id: UUID): Map<String, Any?>? {
    return this.sql("select * from chats where id=:id")
        .param("id", id)
        .query()
        .listOfRows()
        .firstOrNull()
}

private fun JdbcClient.loadChatMemberRows(chatId: UUID): List<Map<String, Any?>> {
    return this.sql("select * from chat_members where chat_id=:chatId")
        .param("chatId", chatId)
        .query()
        .listOfRows()
}

private fun JdbcClient.countTxOutbox(chatId: UUID): Long {
    return this.sql("SELECT COUNT(*) FROM message_outbox WHERE key=?")
        .params(chatId.toString())
        .query(Long::class)
        .single()
}

private fun <T : Any> JdbcClient.StatementSpec.query(mappedClass: KClass<T>) = this.query(mappedClass.java)
