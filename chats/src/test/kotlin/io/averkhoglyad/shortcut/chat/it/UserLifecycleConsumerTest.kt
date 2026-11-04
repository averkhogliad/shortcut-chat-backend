package io.averkhoglyad.shortcut.chat.it

import tools.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.chat.TestcontainersConfiguration
import io.averkhoglyad.shortcut.chat.core.model.UserCreatedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserDeletedPayload
import io.averkhoglyad.shortcut.chat.core.model.UserUpdatedPayload
import io.averkhoglyad.shortcut.common.test.executeSql
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.time.Duration.Companion.seconds

private const val CLEAR = "/it/user-lifecycle/clear.sql"
private const val TOPIC = "UserLifecycle"

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class UserLifecycleConsumerTest(
    jdbc: JdbcClient,
    kafkaProducer: KafkaProducer<String, Any>,
    mapper: ObjectMapper,
) : FreeSpec({

    beforeEach { executeSql(CLEAR) }

    "consumes UserCreated and inserts projection" {
        val id = UUID.randomUUID()
        val eventTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        kafkaProducer.send(producerRecord("UserCreated", id, eventTime, UserCreatedPayload(id, "Created", "created@e.com"))).get()

        eventually(10.seconds) {
            val row = jdbc.loadUserRow(id).shouldNotBeNull()
            row["name"] shouldBe "Created"
            row["email"] shouldBe "created@e.com"
            row["is_deleted"] shouldBe false
            row["last_sync_at"]!!.asInstantColumn() shouldBe eventTime
        }
    }

    "consumes UserUpdated and updates projection" {
        val id = UUID.randomUUID()
        val createdTime = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS)
        val updatedTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        jdbc.insertUserProjection(id, createdTime, "Old", "old@e.com", false)

        kafkaProducer.send(producerRecord("UserUpdated", id, updatedTime, UserUpdatedPayload(id, "Updated", "updated@e.com"))).get()

        eventually(10.seconds) {
            val row = jdbc.loadUserRow(id).shouldNotBeNull()
            row["name"] shouldBe "Updated"
            row["email"] shouldBe "updated@e.com"
            row["last_sync_at"]!!.asInstantColumn() shouldBe updatedTime
        }
    }

    "ignores stale UserUpdated" {
        val id = UUID.randomUUID()
        val newerTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val olderTime = newerTime.minusSeconds(60)
        jdbc.insertUserProjection(id, newerTime, "New", "new@e.com", false)

        kafkaProducer.send(producerRecord("UserUpdated", id, olderTime, UserUpdatedPayload(id, "Old", "old@e.com"))).get()

        Thread.sleep(1_000)
        val row = jdbc.loadUserRow(id).shouldNotBeNull()
        row["name"] shouldBe "New"
        row["email"] shouldBe "new@e.com"
    }

    "consumes UserDeleted and marks projection deleted" {
        val id = UUID.randomUUID()
        val createdTime = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS)
        val deletedTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        jdbc.insertUserProjection(id, createdTime, "ToDelete", "to@e.com", false)

        kafkaProducer.send(producerRecord("UserDeleted", id, deletedTime, UserDeletedPayload(id))).get()

        eventually(10.seconds) {
            val row = jdbc.loadUserRow(id).shouldNotBeNull()
            row["is_deleted"] shouldBe true
            row["last_sync_at"]!!.asInstantColumn() shouldBe deletedTime
        }
    }

    "idempotent duplicate UserCreated does not corrupt row" {
        val id = UUID.randomUUID()
        val eventTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        kafkaProducer.send(producerRecord("UserCreated", id, eventTime, UserCreatedPayload(id, "Created", "created@e.com"))).get()
        eventually(10.seconds) {
            jdbc.loadUserRow(id).shouldNotBeNull()
        }

        kafkaProducer.send(
            producerRecord("UserUpdated", id, eventTime.plusSeconds(1), UserUpdatedPayload(id, "Updated", "updated@e.com"))
        ).get()
        eventually(10.seconds) {
            val row = jdbc.loadUserRow(id).shouldNotBeNull()
            row["name"] shouldBe "Updated"
        }

        kafkaProducer.send(producerRecord("UserCreated", id, eventTime, UserCreatedPayload(id, "Created", "created@e.com"))).get()
        Thread.sleep(1_000)
        jdbc.loadUserRow(id).shouldNotBeNull().let {
            it["name"] shouldBe "Updated"
        }
    }
}) {
    init {
        failfast
    }
}

private fun producerRecord(
    eventName: String,
    id: UUID,
    eventTime: Instant,
    payload: Any,
): ProducerRecord<String, Any> {
    val headers = RecordHeaders().apply {
        add("X-Event-Name", eventName.toByteArray(Charsets.UTF_8))
        add("X-Event-Id", UUID.randomUUID().toString().toByteArray(Charsets.UTF_8))
        add("X-Event-Version", "v1".toByteArray(Charsets.UTF_8))
        add("X-Published-At", eventTime.toString().toByteArray(Charsets.UTF_8))
        add("X-Published-By", "users-app".toByteArray(Charsets.UTF_8))
    }
    return ProducerRecord(TOPIC, null, null, id.toString(), payload, headers)
}

private fun JdbcClient.loadUserRow(id: UUID): Map<String, Any?>? {
    return this.sql("select * from users where id=:id")
        .param("id", id)
        .query()
        .listOfRows()
        .firstOrNull()
}

private fun JdbcClient.insertUserProjection(
    id: UUID,
    lastSyncAt: Instant,
    name: String,
    email: String,
    deleted: Boolean,
) {
    this.sql(
        """
        insert into users (id, external_id, name, email, last_sync_at, is_deleted)
        values (:id, :externalId, :name, :email, :lastSyncAt, :deleted)
        """.trimIndent()
    )
        .param("id", id)
        .param("externalId", id.toString())
        .param("name", name)
        .param("email", email)
        .param("lastSyncAt", java.sql.Timestamp.from(lastSyncAt))
        .param("deleted", deleted)
        .update()
}

private fun Any.asInstantColumn(): Instant {
    return (this as java.sql.Timestamp).toInstant()
}
