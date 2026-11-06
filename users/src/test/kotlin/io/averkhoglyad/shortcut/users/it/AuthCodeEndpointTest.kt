package io.averkhoglyad.shortcut.users.it

import io.averkhoglyad.shortcut.users.TestcontainersConfiguration
import io.averkhoglyad.shortcut.users.core.service.OtpRequestResult
import io.averkhoglyad.shortcut.users.core.service.OtpService
import io.averkhoglyad.shortcut.users.core.service.OtpVerificationResult
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.UUID

private const val STUB_OTP_CODE = "123456"
private const val TEST_EMAIL_PREFIX = "otp-it-"

/**
 * Verifies the endpoint against the real outbox write seam: the emitter, the writer and its
 * `@Transactional(propagation = MANDATORY)` contract run for real, so a missing transaction
 * around the emit fails this test instead of hiding behind a mocked emitter.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class, StubOtpServiceConfiguration::class)
class AuthCodeEndpointTest(
    private val mvc: MockMvc,
    private val jdbc: JdbcClient,
    private val mapper: ObjectMapper,
) : FreeSpec({

    afterEach {
        jdbc.clearOutbox()
        jdbc.deleteUsersByPrefix(TEST_EMAIL_PREFIX)
    }

    "POST /auth/code" - {

        "returns 202 and commits exactly one SendEmail outbox record carrying the code" {
            // given
            val email = testEmail()

            // when
            mvc.perform(
                post("/auth/code")
                    .contentType(APPLICATION_JSON)
                    .content("""{"email":"$email"}""")
            )
                .andExpect(status().isAccepted)
                .andExpect(content().string(""))

            // then
            jdbc.countOutbox() shouldBe 1L

            val record = jdbc.singleOutboxRecord().shouldNotBeNull()
            record["topic"] shouldBe "MailerCommands"
            record["key"] shouldBe null

            val headers = mapper.readTree(record["headers"].toString())
            headers["X-Event-Name"].stringValue() shouldBe "SendEmail"
            headers["X-Event-Version"].stringValue() shouldBe "v1"

            val body = mapper.readTree(record["body"].toString())
            body["to"].stringValues() shouldBe listOf(email)
            body["subject"].stringValue() shouldBe "Your code"
            body["body"].stringValue() shouldBe STUB_OTP_CODE
        }

        "returns 202 for a known and an unknown email without creating a user" {
            // given
            val knownEmail = testEmail()
            val unknownEmail = testEmail()
            jdbc.insertUser(knownEmail)

            // when
            listOf(knownEmail, unknownEmail).forEach { email ->
                mvc.perform(
                    post("/auth/code")
                        .contentType(APPLICATION_JSON)
                        .content("""{"email":"$email"}""")
                )
                    .andExpect(status().isAccepted)
                    .andExpect(content().string(""))
            }

            // then
            jdbc.countUsersByEmail(knownEmail) shouldBe 1L
            jdbc.countUsersByEmail(unknownEmail) shouldBe 0L
            jdbc.countOutbox() shouldBe 2L
            jdbc.countOutboxEventsOtherThan("SendEmail") shouldBe 0L
        }

        "returns 400 and writes no outbox record when the email is blank" {
            // when/then
            mvc.perform(
                post("/auth/code")
                    .contentType(APPLICATION_JSON)
                    .content("""{"email":"   "}""")
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

            jdbc.countOutbox() shouldBe 0L
        }
    }
})

@TestConfiguration(proxyBeanMethods = false)
class StubOtpServiceConfiguration {

    @Bean
    @Primary
    fun stubOtpService(): OtpService = object : OtpService {

        override fun requestCode(email: String): OtpRequestResult =
            OtpRequestResult.CodeSent(STUB_OTP_CODE)

        override fun verifyCode(email: String, code: String): OtpVerificationResult =
            OtpVerificationResult.INVALID
    }
}

private fun testEmail(): String = "$TEST_EMAIL_PREFIX${UUID.randomUUID()}@example.com"

private fun JdbcClient.countOutbox(): Long =
    this.sql("select count(*) from message_outbox")
        .query(Long::class.java)
        .single()

private fun JdbcClient.countOutboxEventsOtherThan(eventName: String): Long =
    this.sql("select count(*) from message_outbox where headers->>'X-Event-Name' <> :eventName")
        .param("eventName", eventName)
        .query(Long::class.java)
        .single()

private fun JdbcClient.singleOutboxRecord(): Map<String, Any?>? =
    this.sql("select * from message_outbox")
        .query()
        .listOfRows()
        .singleOrNull()

private fun JdbcClient.countUsersByEmail(email: String): Long =
    this.sql("select count(*) from users where email=:email")
        .param("email", email)
        .query(Long::class.java)
        .single()

private fun JdbcClient.insertUser(email: String) {
    this.sql("insert into users (id, name, email) values (:id, :name, :email)")
        .param("id", UUID.randomUUID())
        .param("name", "Existing User")
        .param("email", email)
        .update()
}

private fun JdbcClient.clearOutbox() {
    this.sql("delete from message_outbox").update()
}

private fun JdbcClient.deleteUsersByPrefix(prefix: String) {
    this.sql("delete from users where email like :pattern")
        .param("pattern", "$prefix%")
        .update()
}

private fun JsonNode.stringValues(): List<String> =
    (0 until size()).map { this[it].stringValue() }
