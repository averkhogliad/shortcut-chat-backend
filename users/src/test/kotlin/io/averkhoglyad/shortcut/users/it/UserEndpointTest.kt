package io.averkhoglyad.shortcut.users.it

import tools.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.common.test.executeSql
import io.averkhoglyad.shortcut.users.TestcontainersConfiguration
import io.averkhoglyad.shortcut.users.core.model.User
import io.kotest.core.spec.style.FreeSpec
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
            mvc.perform(get("/users/$userId").with(jwtFor(userId)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("User One"))
                .andExpect(jsonPath("$.email").value("user1@example.com"))
        }

        "returns ProblemDetail for missing user" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000999")

            // when
            mvc.perform(get("/users/$userId").with(jwtFor(userId)))
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }

        "returns 401 without a token" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

            // when/then
            mvc.perform(get("/users/$userId"))
                .andExpect(status().isUnauthorized)
        }
    }

    "GET /users/by-email" - {
        "returns existing user" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000002")

            // when
            mvc.perform(get("/users/by-email").queryParam("email", "user2@example.com").with(jwtFor(userId)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000002"))
                .andExpect(jsonPath("$.name").value("User Two"))
        }

        "returns ProblemDetail for missing user" {
            // when
            mvc.perform(get("/users/by-email").queryParam("email", "missing@example.com").with(jwtFor(UUID.randomUUID())))
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }

        "returns 401 without a token" {
            // when/then
            mvc.perform(get("/users/by-email").queryParam("email", "user2@example.com"))
                .andExpect(status().isUnauthorized)
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
                    .with(jwtFor(UUID.randomUUID()))
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

            jdbc.findUserRow(requireNotNull(created.id)).shouldNotBeNull {
                this["name"] shouldBe "New User"
                this["email"] shouldBe email
            }
            jdbc.countOutboxRecords() shouldBe 2L
            jdbc.findOutboxRecordByEventName("UserCreated").shouldNotBeNull {
                this["key"] shouldBe requireNotNull(created.id).toString()
            }
            jdbc.findOutboxRecordByEventName("SendEmail").shouldNotBeNull {
                this["key"] shouldBe null
            }
        }

        "returns 400 when id is passed in body" {
            // given
            val request = User(id = UUID.randomUUID(), name = "New User", email = "new@example.com")

            // when
            mvc.perform(
                post("/users")
                    .with(jwtFor(UUID.randomUUID()))
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
                    .with(jwtFor(UUID.randomUUID()))
                    .contentType(APPLICATION_JSON)
                    .content("""{"email":"new@example.com"}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }

        "returns 401 without a token" {
            // given
            val request = User(id = null, name = "New User", email = "new@example.com")

            // when/then
            mvc.perform(
                post("/users")
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isUnauthorized)

            jdbc.countOutboxRecords() shouldBe 0L
        }
    }

    "PUT /users/{id}" - {
        "updates existing user and writes UserUpdated outbox record" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val request = User(id = null, name = "Updated User", email = "user1@example.com")

            // when
            mvc.perform(
                put("/users/$userId")
                    .with(jwtFor(userId))
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.name").value("Updated User"))
                .andExpect(jsonPath("$.email").value("user1@example.com"))

            // then
            jdbc.findUserRow(userId).shouldNotBeNull {
                this["name"] shouldBe "Updated User"
                this["email"] shouldBe "user1@example.com"
            }
            jdbc.countOutboxRecords() shouldBe 1L
            jdbc.findOutboxRecordByEventName("UserUpdated").shouldNotBeNull {
                this["topic"] shouldBe "UserLifecycle"
                this["key"] shouldBe userId.toString()
            }
        }

        "returns 400 and leaves the user unchanged when email changes" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val request = User(id = null, name = "Updated User", email = "changed@example.com")

            // when
            mvc.perform(
                put("/users/$userId")
                    .with(jwtFor(userId))
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

            // then
            jdbc.findUserRow(userId).shouldNotBeNull {
                this["name"] shouldBe "User One"
                this["email"] shouldBe "user1@example.com"
            }
            jdbc.countOutboxRecords() shouldBe 0L
        }

        "returns 400 when body id differs from path id" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val request = User(id = UUID.randomUUID(), name = "Updated User", email = "updated@example.com")

            // when
            mvc.perform(
                put("/users/$userId")
                    .with(jwtFor(userId))
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
                    .with(jwtFor(userId))
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }

        "returns 403 and leaves the user unchanged when the path id is another user" {
            // given
            val callerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val otherId = UUID.fromString("00000000-0000-0000-0000-000000000002")
            val request = User(id = null, name = "Hijacked User", email = "user2@example.com")

            // when
            mvc.perform(
                put("/users/$otherId")
                    .with(jwtFor(callerId))
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isForbidden)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

            // then
            jdbc.findUserRow(otherId).shouldNotBeNull {
                this["name"] shouldBe "User Two"
                this["email"] shouldBe "user2@example.com"
            }
            jdbc.countOutboxRecords() shouldBe 0L
        }

        "returns 401 without a token" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val request = User(id = null, name = "Updated User", email = "user1@example.com")

            // when/then
            mvc.perform(
                put("/users/$userId")
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isUnauthorized)

            jdbc.countOutboxRecords() shouldBe 0L
        }
    }

    "DELETE /users/{id}" - {
        "deletes existing user and writes UserDeleted outbox record" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

            // when
            mvc.perform(delete("/users/$userId").with(jwtFor(userId)))
                .andExpect(status().isNoContent)

            // then
            jdbc.findUserRow(userId) shouldBe null
            jdbc.countOutboxRecords() shouldBe 1L
            val outbox = jdbc.findOutboxRecordByEventName("UserDeleted").shouldNotBeNull()
            outbox["topic"] shouldBe "UserLifecycle"
            outbox["key"] shouldBe userId.toString()
            val bodyNode = mapper.readTree(outbox["body"].toString())
            bodyNode["id"].stringValue() shouldBe userId.toString()
        }

        "returns ProblemDetail for missing user" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000999")

            // when
            mvc.perform(delete("/users/$userId").with(jwtFor(userId)))
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

            // then
            jdbc.countOutboxRecords() shouldBe 0L
        }

        "returns 403 and keeps the user when the path id is another user" {
            // given
            val callerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
            val otherId = UUID.fromString("00000000-0000-0000-0000-000000000002")

            // when
            mvc.perform(delete("/users/$otherId").with(jwtFor(callerId)))
                .andExpect(status().isForbidden)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

            // then
            jdbc.findUserRow(otherId).shouldNotBeNull {
                this["name"] shouldBe "User Two"
            }
            jdbc.countOutboxRecords() shouldBe 0L
        }

        "returns 401 without a token" {
            // given
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

            // when/then
            mvc.perform(delete("/users/$userId"))
                .andExpect(status().isUnauthorized)

            jdbc.findUserRow(userId).shouldNotBeNull()
            jdbc.countOutboxRecords() shouldBe 0L
        }
    }
})

