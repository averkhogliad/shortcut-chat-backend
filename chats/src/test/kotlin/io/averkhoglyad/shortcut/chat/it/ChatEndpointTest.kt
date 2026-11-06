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
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*
import kotlin.reflect.KClass

private const val INIT = "/it/chat/init.sql"
private const val CLEAR = "/it/chat/clear.sql"

private val USER_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001")

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
                    .with(jwtFor(userId))
                    .queryParam("pageSize", "2"))

            // then
            response
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.data[0].name").value("First Chat"))
                .andExpect(jsonPath("$.data[0].owner.id").value(userId.toString()))
                .andExpect(jsonPath("$.data[0].owner.email").value("user1@example.com"))
                .andExpect(jsonPath("$.data[0].owner.deleted").value(false))
                .andExpect(jsonPath("$.data[1].id").value("00000000-0000-0000-0000-000000000003"))
                .andExpect(jsonPath("$.data[1].owner.id").value(userId.toString()))
                .andExpect(jsonPath("$.next").exists())
        }

        "returns next page after lastId" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val lastId = UUID.fromString("00000000-0000-0000-0000-000000000002")

            // when
            mvc.perform(get("/chats")
                    .with(jwtFor(userId))
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
            mvc.perform(get("/chats/$chatId").with(jwtFor(USER_ONE)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(chatId.toString()))
                .andExpect(jsonPath("$.name").value("First Chat"))
                .andExpect(jsonPath("$.owner.id").value(USER_ONE.toString()))
                .andExpect(jsonPath("$.owner.name").value("User One"))
                .andExpect(jsonPath("$.owner.email").value("user1@example.com"))
                .andExpect(jsonPath("$.members.length()").value(2))
        }

        "returns 404 for non-existent chat" {
            // given
            val chatId = UUID.fromString("00000000-0000-0000-0000-000000000999")

            // when
            mvc.perform(get("/chats/$chatId").with(jwtFor(USER_ONE)))
                .andExpect(status().isNotFound)
        }

        "returns deleted member with marker and active member without it" {
            // given
            val chatId = UUID.fromString("00000000-0000-0000-0000-000000000004")
            val deletedUserId = UUID.fromString("00000000-0000-0000-0000-000000000004")
            val activeUserId = UUID.fromString("00000000-0000-0000-0000-000000000002")

            // when
            val result = mvc.perform(get("/chats/$chatId").with(jwtFor(USER_ONE)))
                .andExpect(status().isOk)
                .andReturn()

            // then
            val details = mapper.readValue(result.response.contentAsString, ChatDetails::class.java)
            details.members shouldHaveSize 2
            withClue("Deleted member") {
                details.members.first { it.id == deletedUserId }.deleted shouldBe true
            }
            withClue("Active member") {
                details.members.first { it.id == activeUserId }.deleted shouldBe false
            }
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
                    .with(jwtFor(USER_ONE))
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("New Chat"))
                .andExpect(jsonPath("$.owner.id").value(USER_ONE.toString()))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andReturn()

            val response = mapper.readValue(result.response.contentAsString, ChatDetails::class.java)

            // then
            val chatRow = jdbc.loadChatRow(response.id)
            withClue("Chat row") {
                chatRow shouldNotBeNull {
                    this shouldContain ("name" to request.name)
                    this shouldContain ("owner_id" to USER_ONE)
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

        "rejects chat with deleted member and leaves no side effects" {
            // given
            val request = ChatRequest(
                name = "Rejected Chat",
                members = listOf(
                    UserRef(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                    UserRef(UUID.fromString("00000000-0000-0000-0000-000000000004"))
                )
            )
            val chatsBefore = jdbc.countRows("chats")
            val chatMembersBefore = jdbc.countRows("chat_members")
            val outboxBefore = jdbc.countRows("message_outbox")

            // when
            val response = mvc.perform(post("/chats")
                    .with(jwtFor(USER_ONE))
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request)))

            // then
            response
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))

            withClue("Chat rows") {
                jdbc.countRows("chats") shouldBe chatsBefore
                jdbc.countChatsByName(request.name) shouldBe 0L
            }
            withClue("Member rows") {
                jdbc.countRows("chat_members") shouldBe chatMembersBefore
            }
            withClue("Outbox rows") {
                jdbc.countRows("message_outbox") shouldBe outboxBefore
            }
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

private fun JdbcClient.countRows(table: String): Long {
    return this.sql("SELECT COUNT(*) FROM $table")
        .query(Long::class)
        .single()
}

private fun JdbcClient.countChatsByName(name: String): Long {
    return this.sql("SELECT COUNT(*) FROM chats WHERE name=:name")
        .param("name", name)
        .query(Long::class)
        .single()
}

private fun <T : Any> JdbcClient.StatementSpec.query(mappedClass: KClass<T>) = this.query(mappedClass.java)
