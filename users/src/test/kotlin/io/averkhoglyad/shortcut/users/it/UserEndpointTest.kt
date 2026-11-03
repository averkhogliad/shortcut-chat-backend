package io.averkhoglyad.shortcut.users.it

import com.fasterxml.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.common.test.executeSql
import io.averkhoglyad.shortcut.users.TestcontainersConfiguration
import io.averkhoglyad.shortcut.users.core.model.User
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

private const val INIT = "/it/user/init.sql"
private const val CLEAR = "/it/user/clear.sql"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class UserEndpointTest(
    private val mvc: MockMvc,
    private val jdbc: JdbcClient,
    private val mapper: ObjectMapper,
) : FreeSpec({

    beforeEach { executeSql(INIT) }
    afterEach { executeSql(CLEAR) }

    "GET /users/{id}" - {
        "returns existing user" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

            // when
            mvc.perform(get("/users/$userId"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("User One"))
                .andExpect(jsonPath("$.email").value("user1@example.com"))
        }

        "returns ProblemDetail for missing user" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000999")

            // when
            mvc.perform(get("/users/$userId"))
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }
    }

    "GET /users/by-email" - {
        "returns existing user" {
            // when
            mvc.perform(get("/users/by-email").queryParam("email", "user2@example.com"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000002"))
                .andExpect(jsonPath("$.name").value("User Two"))
        }

        "returns ProblemDetail for missing user" {
            // when
            mvc.perform(get("/users/by-email").queryParam("email", "missing@example.com"))
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }
    }

    "POST /users" - {
        "creates user and writes lifecycle outbox records" {
            // given
            val email = "new-${UUID.randomUUID()}@example.com"
            val request = User(id = null, name = "New User", email = email)

            // when
            val response = mvc.perform(
                post("/users")
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").isNotEmpty)
                .andExpect(jsonPath("$.name").value("New User"))
                .andExpect(jsonPath("$.email").value(email))
                .andReturn()

            // then
            val created = mapper.readValue(response.response.contentAsString, User::class.java)
            created.id.shouldNotBeNull()

            jdbc.loadUserRow(requireNotNull(created.id)).shouldNotBeNull {
                this["name"] shouldBe "New User"
                this["email"] shouldBe email
            }
            jdbc.countOutbox() shouldBe 2L
        }

        "returns 400 when id is passed in body" {
            // given
            val request = User(id = UUID.randomUUID(), name = "New User", email = "new@example.com")

            // when
            mvc.perform(
                post("/users")
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }

        "returns 400 for unreadable body" {
            // when
            mvc.perform(
                post("/users")
                    .contentType(APPLICATION_JSON)
                    .content("""{"email":"new@example.com"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }
    }

    "PUT /users/{id}" - {
        "updates existing user and writes UserUpdated outbox record" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val request = User(id = null, name = "Updated User", email = "updated@example.com")

            // when
            mvc.perform(
                put("/users/$userId")
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("Updated User"))

            // then
            jdbc.loadUserRow(userId).shouldNotBeNull {
                this["name"] shouldBe "Updated User"
                this["email"] shouldBe "updated@example.com"
            }
            jdbc.countOutbox() shouldBe 1L
            jdbc.findOutboxByEventName("UserUpdated").shouldNotBeNull {
                this["topic"] shouldBe "UserLifecycle"
            }
        }

        "returns 400 when body id differs from path id" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val request = User(id = UUID.randomUUID(), name = "Updated User", email = "updated@example.com")

            // when
            mvc.perform(
                put("/users/$userId")
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }

        "returns ProblemDetail for missing user" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000999")
            val request = User(id = null, name = "Updated User", email = "updated@example.com")

            // when
            mvc.perform(
                put("/users/$userId")
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }
    }

    "DELETE /users/{id}" - {
        "deletes existing user and writes UserDeleted outbox record" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

            // when
            mvc.perform(delete("/users/$userId"))
                .andExpect(status().isNoContent)

            // then
            jdbc.findUser(userId) shouldBe null
            jdbc.countOutbox() shouldBe 1L
            val outbox = jdbc.findOutboxByEventName("UserDeleted").shouldNotBeNull()
            outbox["topic"] shouldBe "UserLifecycle"
            val bodyNode = mapper.readTree(outbox["body"].toString())
            bodyNode["id"].asText() shouldBe userId.toString()
        }

        "returns ProblemDetail for missing user" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000999")

            // when
            mvc.perform(delete("/users/$userId"))
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

            // then
            jdbc.countOutbox() shouldBe 0L
        }
    }
})

private fun JdbcClient.loadUserRow(id: UUID): Map<String, Any?>? =
    this.sql("select * from users where id=:id")
        .param("id", id)
        .query()
        .listOfRows()
        .firstOrNull()

private fun JdbcClient.findUser(id: UUID): Map<String, Any?>? =
    this.sql("select * from users where id=:id")
        .param("id", id)
        .query()
        .listOfRows()
        .firstOrNull()

private fun JdbcClient.countOutbox(): Long =
    this.sql("select count(*) from message_outbox")
        .query(Long::class.java)
        .single()

private fun JdbcClient.findOutboxByEventName(eventName: String): Map<String, Any?>? =
    this.sql("select * from message_outbox where headers->>'X-Event-Name' = :eventName")
        .param("eventName", eventName)
        .query()
        .listOfRows()
        .firstOrNull()
