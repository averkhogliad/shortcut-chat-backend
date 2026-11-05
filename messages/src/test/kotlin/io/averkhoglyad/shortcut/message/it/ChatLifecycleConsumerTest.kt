package io.averkhoglyad.shortcut.message.it

import io.averkhoglyad.shortcut.common.test.asInstantColumn
import io.averkhoglyad.shortcut.common.test.executeSql
import io.averkhoglyad.shortcut.message.TestcontainersConfiguration
import io.averkhoglyad.shortcut.message.core.service.chatCreatedPayloads
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.uuid
import io.kotest.property.exhaustive.of
import org.apache.kafka.clients.producer.KafkaProducer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.seconds

private const val CLEAR = "/it/chat-lifecycle/clear.sql"

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration::class)
class ChatLifecycleConsumerTest(
    jdbc: JdbcClient,
    kafkaProducer: KafkaProducer<String, Any>,
) : FreeSpec({

    beforeEach { executeSql(CLEAR) }

    "consumes ChatCreated and inserts chat projection" {
        // given
        val payload = Arb.chatCreatedPayloads().next()
        val eventTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        // when
        kafkaProducer.send(chatCreatedRecord(payload, eventTime)).get()

        // then
        eventually(10.seconds) {
            val row = jdbc.loadChatRow(payload.id).shouldNotBeNull()
            row["name"] shouldBe payload.name
            row["is_deleted"] shouldBe false
            row["last_sync_at"]!!.asInstantColumn() shouldBe eventTime
        }
        jdbc.countChatMembers(payload.id) shouldBe 0
    }

    "ignores duplicate ChatCreated with same event time" {
        // given
        val created = Arb.chatCreatedPayloads().next()
        val duplicate = Arb.chatCreatedPayloads(
            ids = Exhaustive.of(created.id),
            names = Exhaustive.of("${created.name}-duplicate"),
        ).next()
        val eventTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        kafkaProducer.send(chatCreatedRecord(created, eventTime)).get()
        eventually(10.seconds) {
            jdbc.loadChatRow(created.id).shouldNotBeNull()
        }

        // when
        kafkaProducer.send(chatCreatedRecord(duplicate, eventTime)).get()

        // then
        Thread.sleep(1_000)
        val row = jdbc.loadChatRow(created.id).shouldNotBeNull()
        row["name"] shouldBe created.name
        row["last_sync_at"]!!.asInstantColumn() shouldBe eventTime
    }

    "ignores stale ChatCreated with older event time" {
        // given
        val newer = Arb.chatCreatedPayloads().next()
        val stale = Arb.chatCreatedPayloads(
            ids = Exhaustive.of(newer.id),
            names = Exhaustive.of("${newer.name}-stale"),
        ).next()
        val newerTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val staleTime = newerTime.minusSeconds(60)
        kafkaProducer.send(chatCreatedRecord(newer, newerTime)).get()
        eventually(10.seconds) {
            jdbc.loadChatRow(newer.id).shouldNotBeNull()
        }

        // when
        kafkaProducer.send(chatCreatedRecord(stale, staleTime)).get()

        // then
        Thread.sleep(1_000)
        val row = jdbc.loadChatRow(newer.id).shouldNotBeNull()
        row["name"] shouldBe newer.name
        row["last_sync_at"]!!.asInstantColumn() shouldBe newerTime
    }

    "ignores unknown event name and keeps consuming" {
        // given
        val id = Arb.uuid().next()
        val unknownEventTime = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS)

        // when
        kafkaProducer.send(
            eventRecord(
                eventName = "ChatUpdated",
                key = id.toString(),
                eventTime = unknownEventTime,
                payload = mapOf("id" to id.toString()),
                publishedBy = "chats-app",
            )
        ).get()

        // then
        Thread.sleep(1_000)
        jdbc.loadChatRow(id).shouldBeNull()

        val payload = Arb.chatCreatedPayloads(ids = Exhaustive.of(id)).next()
        val eventTime = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        kafkaProducer.send(chatCreatedRecord(payload, eventTime)).get()
        eventually(10.seconds) {
            val row = jdbc.loadChatRow(id).shouldNotBeNull()
            row["name"] shouldBe payload.name
            row["last_sync_at"]!!.asInstantColumn() shouldBe eventTime
        }
    }
}) {
    init {
        failfast
    }
}
