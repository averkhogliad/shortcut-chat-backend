package io.averkhoglyad.shortcut.outbox.persistence

import tools.jackson.databind.ObjectMapper
import io.averkhoglyad.shortcut.common.test.asInstantColumn
import io.averkhoglyad.shortcut.common.test.betweenInclusive
import io.averkhoglyad.shortcut.outbox.core.OutboxEntry
import io.averkhoglyad.shortcut.outbox.core.OutboxEntryGen
import io.averkhoglyad.shortcut.outbox.core.outboxEntries
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.javaInstant
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.uuid
import io.kotest.property.exhaustive.of
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.sql.DataSource

private const val SCHEMA = "classpath:io/averkhoglyad/shortcut/outbox/migrations/message-outbox.sql"

class OutboxDaoIntegrationTest : FreeSpec({

    lateinit var postgres: PostgreSQLContainer
    lateinit var dataSource: DataSource
    lateinit var jdbc: JdbcClient
    lateinit var target: OutboxDao

    val objectMapper = ObjectMapper()

    beforeSpec {
        postgres = PostgreSQLContainer(DockerImageName.parse("postgres:14-alpine"))
        postgres.start()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        ResourceDatabasePopulator(DefaultResourceLoader().getResource(SCHEMA)).execute(dataSource)
        jdbc = JdbcClient.create(dataSource)
        target = OutboxDao(jdbc, objectMapper)
    }

    afterSpec {
        postgres.stop()
    }

    afterTest {
        jdbc.sql("DELETE FROM message_outbox").update()
    }

    "insert" - {
        "stores the entry as NEW with body and headers in JSONB" {
            // given
            val entry = Arb.outboxEntries().next()
            val startedAt = Instant.now()

            // when
            target.insert(entry)
            val finishedAt = Instant.now()

            // then
            val row = jdbc.singleRow().shouldNotBeNull()
            (row["id"] as UUID).version() shouldBe 7
            row["topic"] shouldBe entry.topic
            row["key"] shouldBe entry.key
            row["state"] shouldBe "NEW"
            row["retry_count"] shouldBe 0
            row["published_at"].shouldBeNull()
            row["lease_owner"].shouldBeNull()
            row["lease_until"].shouldBeNull()
            row["next_attempt_at"].shouldBeNull()
            row.instant("created_at") shouldBe betweenInclusive(startedAt, finishedAt)

            objectMapper.readTree(row["body_text"] as String) shouldBe objectMapper.readTree(entry.body)
            objectMapper.readValue(row["headers_text"] as String, Map::class.java) shouldBe entry.headers

            val types = jdbc.singleRow(
                "SELECT pg_typeof(body)::text AS body_type, pg_typeof(headers)::text AS headers_type FROM message_outbox"
            ).shouldNotBeNull()
            types["body_type"] shouldBe "jsonb"
            types["headers_type"] shouldBe "jsonb"
        }

        "keeps the supplied id and createdAt" {
            // given
            val entry = testEntry(
                id = Arb.uuid().next(),
                createdAt = Arb.javaInstant(Instant.parse("2020-01-01T00:00:00Z")..Instant.parse("2025-01-01T00:00:00Z"))
                    .map { it.truncatedTo(ChronoUnit.MILLIS) }
                    .next(),
            )

            // when
            target.insert(entry.entry)

            // then
            val row = jdbc.row(entry.id).shouldNotBeNull()
            row.instant("created_at") shouldBe entry.entry.createdAt
        }
    }

    "claimBatch" - {
        "claims NEW entries oldest first up to the limit and leases them" {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val first = testEntry(createdAt = now.minusSeconds(30))
            val second = testEntry(createdAt = now.minusSeconds(20))
            val third = testEntry(createdAt = now.minusSeconds(10))
            target.insert(first.entry)
            target.insert(second.entry)
            target.insert(third.entry)
            val leaseUntil = now.plusSeconds(30)

            // when
            val claimed = target.claimBatch("relay-1", leaseUntil, now, 2)

            // then
            claimed.map { it.entry.id } shouldContainExactly listOf(first.id, second.id)
            claimed.forEach { it.retryCount shouldBe 0 }
            claimed[0].entry.body.let { objectMapper.readTree(it) } shouldBe objectMapper.readTree(first.entry.body)
            claimed[0].entry.headers shouldBe first.entry.headers

            listOf(first.id, second.id).forEach { id ->
                val row = jdbc.row(id).shouldNotBeNull()
                row["state"] shouldBe "CLAIMED"
                row["lease_owner"] shouldBe "relay-1"
                row.instant("lease_until") shouldBe leaseUntil
            }
            jdbc.row(third.id).shouldNotBeNull()["state"] shouldBe "NEW"
        }

        "skips rows locked by another transaction" {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val locked = testEntry(createdAt = now.minusSeconds(20))
            val free = testEntry(createdAt = now.minusSeconds(10))
            target.insert(locked.entry)
            target.insert(free.entry)

            val connection = dataSource.connection
            try {
                connection.autoCommit = false
                connection.prepareStatement("SELECT id FROM message_outbox WHERE id = ? FOR UPDATE").use { statement ->
                    statement.setObject(1, locked.id)
                    statement.executeQuery().use { result -> result.next() shouldBe true }
                }

                // when
                val claimed = target.claimBatch("relay-1", now.plusSeconds(30), now, 10)

                // then
                claimed.map { it.entry.id } shouldContainExactly listOf(free.id)
            } finally {
                connection.rollback()
                connection.close()
            }
        }
    }

    "releaseDueRetries" - {
        "returns only due retry rows to NEW" {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val due = testEntry(createdAt = now.minusSeconds(60))
            val notDue = testEntry(createdAt = now.minusSeconds(60))
            target.insert(due.entry)
            target.insert(notDue.entry)
            jdbc.setRetryWait(due.id, now.minusSeconds(5))
            jdbc.setRetryWait(notDue.id, now.plusSeconds(60))

            // when
            target.releaseDueRetries(now)

            // then
            val dueRow = jdbc.row(due.id).shouldNotBeNull()
            dueRow["state"] shouldBe "NEW"
            dueRow["next_attempt_at"].shouldBeNull()

            val notDueRow = jdbc.row(notDue.id).shouldNotBeNull()
            notDueRow["state"] shouldBe "RETRY_WAIT"
            notDueRow.instant("next_attempt_at") shouldBe now.plusSeconds(60)
        }
    }

    "markPublished" - {
        "marks claimed entries published and clears the lease" {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val entry = testEntry(createdAt = now.minusSeconds(30))
            target.insert(entry.entry)
            target.claimBatch("relay-1", now.plusSeconds(30), now, 10)
            val startedAt = Instant.now()

            // when
            target.markPublished(listOf(entry.id))

            // then
            val finishedAt = Instant.now()
            val row = jdbc.row(entry.id).shouldNotBeNull()
            row["state"] shouldBe "PUBLISHED"
            row.instant("published_at") shouldBe betweenInclusive(startedAt, finishedAt)
            row["lease_owner"].shouldBeNull()
            row["lease_until"].shouldBeNull()
        }
    }

    "markRetry" - {
        "schedules the next attempt and stores the retry count" {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val entry = testEntry(createdAt = now.minusSeconds(30))
            target.insert(entry.entry)
            target.claimBatch("relay-1", now.plusSeconds(30), now, 10)
            val nextAttemptAt = now.plusSeconds(120)

            // when
            target.markRetry(entry.id, nextAttemptAt, 4)

            // then
            val row = jdbc.row(entry.id).shouldNotBeNull()
            row["state"] shouldBe "RETRY_WAIT"
            row.instant("next_attempt_at") shouldBe nextAttemptAt
            row["retry_count"] shouldBe 4
            row["lease_owner"].shouldBeNull()
            row["lease_until"].shouldBeNull()
        }
    }

    "markDead" - {
        "marks the entry dead and clears the lease" {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val entry = testEntry(createdAt = now.minusSeconds(30))
            target.insert(entry.entry)
            target.claimBatch("relay-1", now.plusSeconds(30), now, 10)

            // when
            target.markDead(entry.id)

            // then
            val row = jdbc.row(entry.id).shouldNotBeNull()
            row["state"] shouldBe "DEAD"
            row["lease_owner"].shouldBeNull()
            row["lease_until"].shouldBeNull()
        }
    }

    "recoverExpiredLeases" - {
        "returns only entries with an expired lease to NEW" {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val expired = testEntry(createdAt = now.minusSeconds(30))
            val active = testEntry(createdAt = now.minusSeconds(30))
            target.insert(expired.entry)
            target.insert(active.entry)
            jdbc.setClaimed(expired.id, "relay-1", now.minusSeconds(1))
            jdbc.setClaimed(active.id, "relay-1", now.plusSeconds(60))

            // when
            target.recoverExpiredLeases(now)

            // then
            val expiredRow = jdbc.row(expired.id).shouldNotBeNull()
            expiredRow["state"] shouldBe "NEW"
            expiredRow["lease_owner"].shouldBeNull()
            expiredRow["lease_until"].shouldBeNull()

            val activeRow = jdbc.row(active.id).shouldNotBeNull()
            activeRow["state"] shouldBe "CLAIMED"
            activeRow["lease_owner"] shouldBe "relay-1"
            activeRow.instant("lease_until") shouldBe now.plusSeconds(60)
        }
    }

    "deleteTerminal" - {
        "deletes only old PUBLISHED and DEAD records" {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val oldPublished = testEntry(createdAt = now.minusSeconds(60))
            val recentPublished = testEntry(createdAt = now.minusSeconds(60))
            val oldDead = testEntry(createdAt = now.minusSeconds(40 * 24 * 3600L))
            val recentDead = testEntry(createdAt = now.minusSeconds(24 * 3600L))
            val oldNew = testEntry(createdAt = now.minusSeconds(100 * 24 * 3600L))
            val oldClaimed = testEntry(createdAt = now.minusSeconds(100 * 24 * 3600L))
            listOf(oldPublished, recentPublished, oldDead, recentDead, oldNew, oldClaimed)
                .forEach { target.insert(it.entry) }

            jdbc.setPublished(oldPublished.id, now.minusSeconds(10 * 24 * 3600L))
            jdbc.setPublished(recentPublished.id, now.minusSeconds(24 * 3600L))
            jdbc.setDead(oldDead.id)
            jdbc.setDead(recentDead.id)
            jdbc.setClaimed(oldClaimed.id, "relay-1", now.plusSeconds(60))

            // when
            target.deleteTerminal(
                publishedBefore = now.minusSeconds(7 * 24 * 3600L),
                deadBefore = now.minusSeconds(30 * 24 * 3600L),
            )

            // then
            jdbc.ids() shouldContainExactlyInAnyOrder
                listOf(recentPublished.id, recentDead.id, oldNew.id, oldClaimed.id)
        }
    }

    "counts" - {
        "count entries by state and due retries" {
            // given
            val now = Instant.now().truncatedTo(ChronoUnit.MICROS)
            val newOne = testEntry(createdAt = now.minusSeconds(60))
            val newTwo = testEntry(createdAt = now.minusSeconds(60))
            val due = testEntry(createdAt = now.minusSeconds(60))
            val notDue = testEntry(createdAt = now.minusSeconds(60))
            val dead = testEntry(createdAt = now.minusSeconds(60))
            listOf(newOne, newTwo, due, notDue, dead).forEach { target.insert(it.entry) }
            jdbc.setRetryWait(due.id, now.minusSeconds(5))
            jdbc.setRetryWait(notDue.id, now.plusSeconds(60))
            jdbc.setDead(dead.id)

            // when
            val newCount = target.countByState("NEW")
            val deadCount = target.countByState("DEAD")
            val dueCount = target.countDueRetries(now)

            // then
            newCount shouldBe 2L
            deadCount shouldBe 1L
            dueCount shouldBe 1L
        }
    }
})

private data class TestEntry(
    val id: UUID,
    val entry: OutboxEntry,
)

private fun testEntry(id: UUID = Arb.uuid().next(), createdAt: Instant? = null): TestEntry = TestEntry(
    id = id,
    entry = Arb.outboxEntries(
        OutboxEntryGen(
            ids = Exhaustive.of(id),
            createdAts = Exhaustive.of(createdAt),
        )
    ).next(),
)

private fun Map<String, Any?>.instant(column: String): Instant =
    requireNotNull(this[column]).asInstantColumn()

private fun JdbcClient.singleRow(
    sql: String = "SELECT *, body::text AS body_text, headers::text AS headers_text FROM message_outbox",
): Map<String, Any?>? =
    this.sql(sql).query().listOfRows().singleOrNull()

private fun JdbcClient.row(id: UUID): Map<String, Any?>? =
    this.sql("SELECT *, body::text AS body_text, headers::text AS headers_text FROM message_outbox WHERE id = :id")
        .param("id", id)
        .query()
        .listOfRows()
        .singleOrNull()

private fun JdbcClient.ids(): List<UUID> =
    this.sql("SELECT id FROM message_outbox")
        .query { rs, _ -> rs.getObject("id", UUID::class.java) }
        .list()

private fun JdbcClient.setRetryWait(id: UUID, nextAttemptAt: Instant) {
    this.sql("UPDATE message_outbox SET state = 'RETRY_WAIT', next_attempt_at = :at WHERE id = :id")
        .param("at", Timestamp.from(nextAttemptAt))
        .param("id", id)
        .update()
}

private fun JdbcClient.setPublished(id: UUID, publishedAt: Instant) {
    this.sql("UPDATE message_outbox SET state = 'PUBLISHED', published_at = :at WHERE id = :id")
        .param("at", Timestamp.from(publishedAt))
        .param("id", id)
        .update()
}

private fun JdbcClient.setDead(id: UUID) {
    this.sql("UPDATE message_outbox SET state = 'DEAD' WHERE id = :id")
        .param("id", id)
        .update()
}

private fun JdbcClient.setClaimed(id: UUID, owner: String, leaseUntil: Instant) {
    this.sql("UPDATE message_outbox SET state = 'CLAIMED', lease_owner = :owner, lease_until = :leaseUntil WHERE id = :id")
        .param("owner", owner)
        .param("leaseUntil", Timestamp.from(leaseUntil))
        .param("id", id)
        .update()
}
