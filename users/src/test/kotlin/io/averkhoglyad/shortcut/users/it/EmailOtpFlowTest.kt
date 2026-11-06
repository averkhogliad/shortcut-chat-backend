package io.averkhoglyad.shortcut.users.it

import io.averkhoglyad.shortcut.outbox.core.OutboxRelay
import io.averkhoglyad.shortcut.users.TestcontainersConfiguration
import io.averkhoglyad.shortcut.users.core.model.User
import io.averkhoglyad.shortcut.users.security.EMAIL_OTP_GRANT_TYPE
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.kafka.ConfluentKafkaContainer
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64
import java.util.UUID
import kotlin.test.fail

private const val ISSUER = "http://localhost:8080"
private const val CLIENT_ID = "test-client"
private const val CLIENT_SECRET = "test-client-secret"
private const val EMAIL_PREFIX = "otp-flow-"
private const val OTP_KEY_PREFIX = "users:otp:"
private const val RATE_LIMIT_KEY_PREFIX = "users:otp:ratelimit:"
private const val OPENID_SCOPE = "openid"
private const val EMAIL_SCOPE = "email"
private const val EVENT_NAME_HEADER = "X-Event-Name"
private const val SEND_EMAIL_EVENT = "SendEmail"
private const val USER_CREATED_EVENT = "UserCreated"
private const val USER_UPDATED_EVENT = "UserUpdated"
private const val USER_LIFECYCLE_TOPIC = "UserLifecycle"
private val KAFKA_TIMEOUT: Duration = Duration.ofSeconds(30)
private val KAFKA_POLL: Duration = Duration.ofMillis(500)

/**
 * End-to-end proof of the OTP/OIDC flow against real Postgres, Redis and Kafka: the code is
 * requested over HTTP, read back from Redis and the outbox, exchanged for tokens at the
 * token endpoint, and the auto-registration `UserCreated` event is published to Kafka by
 * driving the relay explicitly (its scheduler is disabled by the `test` profile).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class EmailOtpFlowTest(
    private val mvc: MockMvc,
    private val jdbc: JdbcClient,
    private val mapper: ObjectMapper,
    private val redis: StringRedisTemplate,
    private val relay: ObjectProvider<OutboxRelay>,
    private val kafka: ConfluentKafkaContainer,
) : FreeSpec({

    val emails = mutableListOf<String>()

    fun newEmail(): String = Arb.otpEmails(EMAIL_PREFIX).next().also { emails += it }

    fun requestCode(email: String) {
        mvc.perform(
            post("/auth/code")
                .contentType(APPLICATION_JSON)
                .content("""{"email":"$email"}""")
        ).andExpect(status().isAccepted)
    }

    fun tokenRequest(email: String, code: String) = mvc.perform(
        post("/oauth2/token")
            .header(AUTHORIZATION, basicAuth())
            .param(OAuth2ParameterNames.GRANT_TYPE, EMAIL_OTP_GRANT_TYPE.value)
            .param("email", email)
            .param(OAuth2ParameterNames.CODE, code)
    )

    afterEach {
        jdbc.deleteOutbox()
        jdbc.deleteUsersByPrefix(EMAIL_PREFIX)
        emails.forEach { email ->
            redis.delete(listOf("$OTP_KEY_PREFIX$email", "$RATE_LIMIT_KEY_PREFIX$email"))
        }
        emails.clear()
    }

    "POST /auth/code" - {

        "returns 429 for a second request within 60 seconds without a new code or email" {
            // given
            val email = newEmail()
            requestCode(email)
            val firstCode = redis.otpCode(email).shouldNotBeNull()
            val sentEmail = jdbc.findOutboxByEventName(SEND_EMAIL_EVENT).shouldNotBeNull()
            mapper.readTree(sentEmail["body"].toString())["body"].stringValue() shouldBe firstCode
            jdbc.countOutboxByEventName(SEND_EMAIL_EVENT) shouldBe 1L

            // when
            mvc.perform(
                post("/auth/code")
                    .contentType(APPLICATION_JSON)
                    .content("""{"email":"$email"}""")
            )
                .andExpect(status().isTooManyRequests)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

            // then
            redis.otpCode(email) shouldBe firstCode
            jdbc.countOutboxByEventName(SEND_EMAIL_EVENT) shouldBe 1L
        }
    }

    "POST /oauth2/token" - {

        "issues access, ID and refresh tokens carrying the required JWT claims" {
            // given
            val email = newEmail()
            requestCode(email)
            val code = redis.otpCode(email).shouldNotBeNull()

            // when
            val body = tokenRequest(email, code)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber)
                .andReturn()
                .response
                .contentAsString
                .let(mapper::readTree)

            // then
            body["expires_in"].longValue() shouldBeGreaterThan 0L
            val accessToken = requireNotNull(body["access_token"].stringValue())
            val idToken = requireNotNull(body["id_token"].stringValue())
            accessToken.shouldNotBeBlank()
            idToken.shouldNotBeBlank()
            requireNotNull(body["refresh_token"].stringValue()).shouldNotBeBlank()
            redis.otpCode(email).shouldBeNull()

            val userId = jdbc.findUserIdByEmail(email).shouldNotBeNull()

            val access = mapper.decodeJwtPayload(accessToken)
            access["sub"].stringValue() shouldBe userId.toString()
            access["iss"].stringValue() shouldBe ISSUER
            access["aud"].stringValues() shouldContain CLIENT_ID
            access["scope"].stringValues() shouldContain OPENID_SCOPE
            access["exp"].longValue() shouldBeGreaterThan 0L
            access["iat"].longValue() shouldBeGreaterThan 0L

            val id = mapper.decodeJwtPayload(idToken)
            id["sub"].stringValue() shouldBe userId.toString()
            id["email"].stringValue() shouldBe email
            id["name"].stringValue() shouldBe email.substringBefore("@")
            id["iss"].stringValue() shouldBe ISSUER
        }

        "auto-registers an unknown email and publishes UserCreated to UserLifecycle" {
            // given
            val email = newEmail()
            val expectedName = email.substringBefore("@")
            requestCode(email)
            val code = redis.otpCode(email).shouldNotBeNull()

            // when
            tokenRequest(email, code).andExpect(status().isOk)

            // then
            val user = jdbc.findUserByEmail(email).shouldNotBeNull()
            user["name"] shouldBe expectedName
            user["email"] shouldBe email
            jdbc.countUsersByEmail(email) shouldBe 1L
            jdbc.countOutboxByEventName(USER_CREATED_EVENT) shouldBe 1L

            // when
            val outboxRelay = relay.getIfAvailable() ?: fail(
                "The tx-outbox runtime is not wired: no OutboxRelay bean exists although Kafka is on the " +
                    "classpath and tx-outbox is enabled. The queued UserCreated record cannot reach Kafka."
            )
            outboxRelay.runCycle()

            // then
            val record = kafka.consumeRecord(USER_LIFECYCLE_TOPIC, KAFKA_TIMEOUT) { candidate ->
                mapper.readTree(requireNotNull(candidate.value()))["email"].stringValue() == email
            }
            record.headers().lastHeaderAsString(EVENT_NAME_HEADER) shouldBe USER_CREATED_EVENT
            record.key() shouldBe user["id"].toString()

            val payload = mapper.readTree(requireNotNull(record.value()))
            payload["id"].stringValue() shouldBe user["id"].toString()
            payload["name"].stringValue() shouldBe expectedName
            payload["email"].stringValue() shouldBe email
        }

        "invalidates the code after three wrong attempts and rejects the correct code" {
            // given
            val email = newEmail()
            requestCode(email)
            val code = redis.otpCode(email).shouldNotBeNull()

            // when
            (1..3).forEach { attempt ->
                tokenRequest(email, wrongCode(code, attempt))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.error").value(OAuth2ErrorCodes.INVALID_GRANT))
            }

            // then
            redis.otpCode(email).shouldBeNull()

            // when
            tokenRequest(email, code)
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error").value(OAuth2ErrorCodes.INVALID_GRANT))

            // then
            jdbc.countUsersByEmail(email) shouldBe 0L
            jdbc.countOutboxByEventName(USER_CREATED_EVENT) shouldBe 0L
        }
    }

    "GET /userinfo" - {

        "returns sub, email and name for an access token obtained through the email-OTP grant" {
            // given
            val email = newEmail()
            requestCode(email)
            val code = redis.otpCode(email).shouldNotBeNull()
            val body = tokenRequest(email, code)
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
                .let(mapper::readTree)
            val accessToken = body["access_token"].stringValue()
            mapper.decodeJwtPayload(accessToken)["scope"].stringValues() shouldContain EMAIL_SCOPE
            val userId = jdbc.findUserIdByEmail(email).shouldNotBeNull()

            // when
            val userInfo = mvc.perform(
                get("/userinfo")
                    .header(AUTHORIZATION, "Bearer $accessToken")
            )
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
                .let(mapper::readTree)

            // then
            userInfo["sub"].stringValue() shouldBe userId.toString()
            userInfo["email"].stringValue() shouldBe email
            userInfo["name"].stringValue() shouldBe email.substringBefore("@")
        }

        "rejects a request without a bearer token with a 4xx status" {
            // when/then
            mvc.perform(get("/userinfo"))
                .andExpect(status().is4xxClientError)
        }
    }

    "PUT /users/{id}" - {

        "rejects an email change for a user registered through the OTP flow" {
            // given
            val email = newEmail()
            requestCode(email)
            val code = redis.otpCode(email).shouldNotBeNull()
            tokenRequest(email, code).andExpect(status().isOk)
            val stored = jdbc.findUserByEmail(email).shouldNotBeNull()
            val userId = UUID.fromString(stored["id"].toString())
            val request = User(
                id = null,
                name = "Changed Name",
                email = Arb.otpEmails("changed-").next(),
            )

            // when
            mvc.perform(
                put("/users/$userId")
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))

            // then
            jdbc.findUserByEmail(email).shouldNotBeNull {
                this["name"] shouldBe stored["name"]
                this["email"] shouldBe email
            }
            jdbc.countOutboxByEventName(USER_UPDATED_EVENT) shouldBe 0L
        }
    }
})

private fun basicAuth(): String {
    val credentials = "$CLIENT_ID:$CLIENT_SECRET".toByteArray(StandardCharsets.UTF_8)
    return "Basic " + Base64.getEncoder().encodeToString(credentials)
}

private fun wrongCode(code: String, attempt: Int): String =
    ((code.toInt() + attempt) % 1_000_000).toString().padStart(6, '0')

private fun ObjectMapper.decodeJwtPayload(token: String): JsonNode =
    readTree(Base64.getUrlDecoder().decode(token.split(".")[1]))

private fun JsonNode.stringValues(): List<String> =
    if (isString) listOf(stringValue()) else values().map { it.stringValue() }

private fun StringRedisTemplate.otpCode(email: String): String? =
    opsForHash<String, String>().get("$OTP_KEY_PREFIX$email", "code")

private fun JdbcClient.countOutboxByEventName(eventName: String): Long =
    sql("select count(*) from message_outbox where headers->>'$EVENT_NAME_HEADER' = :eventName")
        .param("eventName", eventName)
        .query(Long::class.java)
        .single()

private fun JdbcClient.findOutboxByEventName(eventName: String): Map<String, Any?>? =
    sql("select * from message_outbox where headers->>'$EVENT_NAME_HEADER' = :eventName")
        .param("eventName", eventName)
        .query()
        .listOfRows()
        .firstOrNull()

private fun JdbcClient.deleteOutbox() {
    sql("delete from message_outbox").update()
}

private fun JdbcClient.countUsersByEmail(email: String): Long =
    sql("select count(*) from users where email=:email")
        .param("email", email)
        .query(Long::class.java)
        .single()

private fun JdbcClient.findUserByEmail(email: String): Map<String, Any?>? =
    sql("select * from users where email=:email")
        .param("email", email)
        .query()
        .listOfRows()
        .firstOrNull()

private fun JdbcClient.findUserIdByEmail(email: String): UUID? =
    findUserByEmail(email)?.get("id") as UUID?

private fun JdbcClient.deleteUsersByPrefix(prefix: String) {
    sql("delete from users where email like :pattern")
        .param("pattern", "$prefix%")
        .update()
}

private fun ConfluentKafkaContainer.consumeRecord(
    topic: String,
    timeout: Duration,
    matches: (ConsumerRecord<String, String>) -> Boolean,
): ConsumerRecord<String, String> {
    val consumer = KafkaConsumer<String, String>(
        mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "users-flow-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        )
    )
    consumer.use {
        it.subscribe(listOf(topic))
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            it.poll(KAFKA_POLL)
                .firstOrNull(matches)
                ?.let { record -> return record }
        }
    }
    fail("No matching record in $topic within $timeout")
}

private fun Headers.lastHeaderAsString(name: String): String? =
    lastHeader(name)?.value()?.toString(StandardCharsets.UTF_8)
