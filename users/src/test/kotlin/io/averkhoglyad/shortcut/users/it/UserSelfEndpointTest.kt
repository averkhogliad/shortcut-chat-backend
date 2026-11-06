package io.averkhoglyad.shortcut.users.it

import io.averkhoglyad.shortcut.common.test.executeSql
import io.averkhoglyad.shortcut.users.TestcontainersConfiguration
import io.averkhoglyad.shortcut.users.core.model.User
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.*

private const val INIT = "/it/user/init.sql"
private const val CLEAR = "/it/user/clear.sql"

private val USER_ONE: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
private val USER_TWO: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

/**
 * Self-service endpoints: the caller is resolved from the JWT `sub` claim only, and the request
 * cannot name another user.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class UserSelfEndpointTest(
    private val mvc: MockMvc,
    private val jdbc: JdbcClient,
    private val mapper: ObjectMapper,
) : FreeSpec({

    beforeEach { executeSql(INIT) }
    afterEach { executeSql(CLEAR) }

    "GET /users/me" - {
        "returns the profile of the token subject" {
            // when
            mvc.perform(get("/users/me").with(jwtFor(USER_ONE)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(USER_ONE.toString()))
                .andExpect(jsonPath("$.name").value("User One"))
                .andExpect(jsonPath("$.email").value("user1@example.com"))
        }

        "returns 401 without a token" {
            // when/then
            mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized)
        }

        "returns ProblemDetail for a subject without a stored user" {
            // given
            val missingId = UUID.fromString("00000000-0000-0000-0000-000000000999")

            // when/then
            mvc.perform(get("/users/me").with(jwtFor(missingId)))
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }

        "returns a client error for a subject that is not a UUID" {
            // given
            val token = jwtFor(USER_ONE).jwt { it.subject("not-a-uuid") }

            // when/then
            mvc.perform(get("/users/me").with(token))
                .andExpect(status().is4xxClientError)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }
    }

    "PUT /users/me" - {
        "updates only the profile of the token subject" {
            // given
            val request = User(id = null, name = "Updated User", email = "user1@example.com")

            // when
            mvc.perform(
                put("/users/me")
                    .with(jwtFor(USER_ONE))
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(USER_ONE.toString()))
                .andExpect(jsonPath("$.name").value("Updated User"))
                .andExpect(jsonPath("$.email").value("user1@example.com"))

            // then
            jdbc.findUserRow(USER_ONE).shouldNotBeNull {
                this["name"] shouldBe "Updated User"
                this["email"] shouldBe "user1@example.com"
            }
            jdbc.findUserRow(USER_TWO).shouldNotBeNull {
                this["name"] shouldBe "User Two"
            }
            jdbc.countOutboxRecords() shouldBe 1L
            jdbc.findOutboxRecordByEventName("UserUpdated").shouldNotBeNull {
                this["key"] shouldBe USER_ONE.toString()
            }
        }

        "returns 400 and leaves the profile unchanged when the email changes" {
            // given
            val request = User(id = null, name = "Updated User", email = "changed@example.com")

            // when
            mvc.perform(
                put("/users/me")
                    .with(jwtFor(USER_ONE))
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

            // then
            jdbc.findUserRow(USER_ONE).shouldNotBeNull {
                this["name"] shouldBe "User One"
                this["email"] shouldBe "user1@example.com"
            }
            jdbc.countOutboxRecords() shouldBe 0L
        }

        "returns 400 when the body id differs from the subject" {
            // given
            val request = User(id = USER_TWO, name = "Updated User", email = "user1@example.com")

            // when
            mvc.perform(
                put("/users/me")
                    .with(jwtFor(USER_ONE))
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

            // then
            jdbc.findUserRow(USER_ONE).shouldNotBeNull {
                this["name"] shouldBe "User One"
            }
            jdbc.countOutboxRecords() shouldBe 0L
        }

        "returns 401 without a token" {
            // given
            val request = User(id = null, name = "Updated User", email = "user1@example.com")

            // when/then
            mvc.perform(
                put("/users/me")
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isUnauthorized)

            jdbc.findUserRow(USER_ONE).shouldNotBeNull {
                this["name"] shouldBe "User One"
            }
        }
    }

    "DELETE /users/me" - {
        "deletes only the profile of the token subject" {
            // when
            mvc.perform(delete("/users/me").with(jwtFor(USER_ONE)))
                .andExpect(status().isNoContent)

            // then
            jdbc.findUserRow(USER_ONE).shouldBeNull()
            jdbc.findUserRow(USER_TWO).shouldNotBeNull()
            jdbc.countOutboxRecords() shouldBe 1L
            jdbc.findOutboxRecordByEventName("UserDeleted").shouldNotBeNull {
                this["key"] shouldBe USER_ONE.toString()
            }
        }

        "returns ProblemDetail for an already deleted profile" {
            // given
            mvc.perform(delete("/users/me").with(jwtFor(USER_ONE)))
                .andExpect(status().isNoContent)

            // when/then
            mvc.perform(delete("/users/me").with(jwtFor(USER_ONE)))
                .andExpect(status().isNotFound)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
        }

        "returns 401 without a token" {
            // when/then
            mvc.perform(delete("/users/me"))
                .andExpect(status().isUnauthorized)

            jdbc.findUserRow(USER_ONE).shouldNotBeNull()
        }
    }
})
