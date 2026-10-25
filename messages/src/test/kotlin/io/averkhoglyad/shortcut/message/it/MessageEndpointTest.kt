package io.averkhoglyad.shortcut.message.it

import com.fasterxml.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.common.test.executeSql
import io.averkhoglyad.shortcut.common.util.toUUID
import io.averkhoglyad.shortcut.message.TestcontainersConfiguration
import io.averkhoglyad.shortcut.message.core.model.ChatRef
import io.averkhoglyad.shortcut.message.core.model.Message
import io.averkhoglyad.shortcut.message.core.model.MessageRequest
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.maps.shouldContainKeys
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.header.Headers
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.kafka.test.utils.KafkaTestUtils
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.*

private const val INIT = "/it/init.sql"
private const val CLEAR = "/it/clear.sql"

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class MessageEndpointTest(
    val mvc: MockMvc,
    val jdbc: JdbcClient,
    val mapper: ObjectMapper,
    val kafkaConsumerProvider: (List<String>) -> KafkaConsumer<String, String>,
) : FreeSpec() {

    init {
        beforeEach { executeSql(INIT) }
        afterEach { executeSql(CLEAR) }

        "GET /messages" - {
            "returns first page if no lastId" {
                // given
                val chatId = "22222222-2222-2222-2222-222222222222"

                // when
                val response = get("/messages")
                    .queryParam("chat", chatId)
                    .queryParam("pageSize", "1")
                    .accept(APPLICATION_JSON)
                    .perform()

                // then
                response
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value("44444444-4444-4444-4444-444444444444"))
                    .andExpect(jsonPath("$.data[0].text").value("Message 2"))
                    .andExpect(jsonPath("$.next").isNotEmpty())
            }

            "returns next page after lastId" {
                // given
                val chatId = "22222222-2222-2222-2222-222222222222"
                val lastId = "44444444-4444-4444-4444-444444444444"

                // when
                val response = get("/messages")
                    .queryParam("chat", chatId)
                    .queryParam("pageSize", "1")
                    .queryParam("lastId", lastId)
                    .accept(APPLICATION_JSON)
                    .perform()

                // then
                response
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value("33333333-3333-3333-3333-333333333333"))
                    .andExpect(jsonPath("$.data[0].text").value("Message 1"))
                    .andExpect(jsonPath("$.next").isNotEmpty())
            }

            "returns empty page after very last lastId" {
                // given
                val chatId = "22222222-2222-2222-2222-222222222222"
                val lastId = "33333333-3333-3333-3333-333333333333"

                // when
                val response = get("/messages")
                    .queryParam("chat", chatId)
                    .queryParam("pageSize", "1")
                    .queryParam("lastId", lastId)
                    .accept(APPLICATION_JSON)
                    .perform()

                // then
                response
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.data.length()").value(0))
                    .andExpect(jsonPath("$.next").isEmpty())
            }
        }

        "POST /messages" - {
            "creates message and publish event to Kafka" {
                // given
                val userId = "11111111-1111-1111-1111-111111111111"
                val chatId = "22222222-2222-2222-2222-222222222222"
                val messageText = "New test message"
                val request = MessageRequest(
                    text = messageText,
                    chat = ChatRef(chatId.toUUID())
                )

                // when
                val response = post("/messages")
                    .header("X-User-Id", userId)
                    .contentType(APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request))
                    .perform()

                // then
                response.andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.text").value(messageText))
                    .andExpect(jsonPath("$.chat.id").value(chatId))
                    .andExpect(jsonPath("$.author.id").value(userId))

                val message = mapper.readValue(response.andReturn().response.contentAsString, Message::class.java)

                val messageRow = jdbc.loadMessageRow(message.id)
                withClue("Message row") {
                    messageRow shouldNotBeNull {
                        this shouldContain ("text" to messageText)
                        this shouldContain ("chat_id" to chatId.toUUID())
                        this shouldContain ("author_id" to userId.toUUID())
                        this.shouldContainKeys("created_at")
                    }
                }

                val record = kafkaConsumerProvider(listOf("ChatLifecycle"))
                    .use { KafkaTestUtils.getSingleRecord(it, "ChatLifecycle") }
                
                withClue("Message Event") {
                    record.key() shouldBe chatId

                    mapper.readValue(record.value(), Message::class.java) should {
                        it.text shouldBe messageText
                        it.chat.id shouldBe chatId.toUUID()
                        it.author.id shouldBe userId.toUUID()
                    }

                    record.headers().lastHeaderAsString("X-Event-Name") shouldBe "MessagePublished"
                }

            }
        }
    }

    private fun RequestBuilder.perform() = mvc.perform(this)
}

private fun JdbcClient.loadMessageRow(id: UUID): Map<String, Any?>? {
    return this.sql("select * from messages where id=:id")
        .param("id", id)
        .query()
        .listOfRows()
        .firstOrNull()
}

fun Headers.lastHeaderAsString(name: String): String? {
    return lastHeader(name)?.value()?.toString(Charsets.UTF_8)
}
