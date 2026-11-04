package io.averkhoglyad.shortcut.outbox.persistence

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.fasterxml.uuid.Generators
import io.averkhoglyad.shortcut.outbox.core.OutboxEntry
import org.postgresql.util.PGobject
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class OutboxDao(
    private val jdbc: JdbcClient,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {

    private val uuidGenerator = Generators.timeBasedEpochGenerator()

    fun insert(entry: OutboxEntry) {
        val id = entry.id ?: uuidGenerator.generate()
        if (entry.createdAt == null) {
            jdbc.sql(INSERT_WITH_NOW)
                .param("id", id)
                .param("topic", entry.topic)
                .param("key", entry.key)
                .param("body", jsonb(entry.body))
                .param("headers", jsonb(encodeHeaders(entry.headers)))
                .update()
        } else {
            jdbc.sql(INSERT_WITH_CREATED_AT)
                .param("id", id)
                .param("topic", entry.topic)
                .param("key", entry.key)
                .param("body", jsonb(entry.body))
                .param("headers", jsonb(encodeHeaders(entry.headers)))
                .param("createdAt", Timestamp.from(entry.createdAt))
                .update()
        }
    }

    fun claimBatch(owner: String, leaseUntil: Instant, before: Instant, limit: Int): List<ClaimedOutboxEntry> {
        val rows = jdbc.sql(CLAIM_SELECT)
            .param("before", Timestamp.from(before))
            .param("limit", limit)
            .query { rs, _ ->
                ClaimedRow(
                    id = rs.getObject("id", UUID::class.java),
                    entry = OutboxEntry(
                        topic = rs.getString("topic"),
                        key = rs.getString("key"),
                        body = rs.getString("body"),
                        headers = decodeHeaders(rs.getString("headers")),
                        id = rs.getObject("id", UUID::class.java),
                        createdAt = rs.getTimestamp("created_at").toInstant(),
                    ),
                    retryCount = rs.getInt("retry_count"),
                )
            }
            .list()
        if (rows.isEmpty()) {
            return emptyList()
        }

        jdbc.sql(CLAIM_UPDATE)
            .param("owner", owner)
            .param("leaseUntil", Timestamp.from(leaseUntil))
            .param("ids", rows.map { it.id })
            .update()

        return rows.map { ClaimedOutboxEntry(it.entry, it.retryCount) }
    }

    fun releaseDueRetries(now: Instant) {
        jdbc.sql(RELEASE_DUE_RETRIES)
            .param("now", Timestamp.from(now))
            .update()
    }

    fun markPublished(ids: Collection<UUID>) {
        if (ids.isEmpty()) {
            return
        }
        jdbc.sql(MARK_PUBLISHED)
            .param("ids", ids)
            .update()
    }

    fun markRetry(id: UUID, nextAttemptAt: Instant, retryCount: Int) {
        jdbc.sql(MARK_RETRY)
            .param("id", id)
            .param("nextAttemptAt", Timestamp.from(nextAttemptAt))
            .param("retryCount", retryCount)
            .update()
    }

    fun markDead(id: UUID) {
        jdbc.sql(MARK_DEAD)
            .param("id", id)
            .update()
    }

    fun recoverExpiredLeases(now: Instant) {
        jdbc.sql(RECOVER_EXPIRED_LEASES)
            .param("now", Timestamp.from(now))
            .update()
    }

    fun deleteTerminal(publishedBefore: Instant, deadBefore: Instant) {
        jdbc.sql(DELETE_TERMINAL)
            .param("publishedBefore", Timestamp.from(publishedBefore))
            .param("deadBefore", Timestamp.from(deadBefore))
            .update()
    }

    fun countByState(state: String): Long {
        return jdbc.sql(COUNT_BY_STATE)
            .param("state", state)
            .query(Long::class.java)
            .single()
    }

    fun countDueRetries(now: Instant): Long {
        return jdbc.sql(COUNT_DUE_RETRIES)
            .param("now", Timestamp.from(now))
            .query(Long::class.java)
            .single()
    }

    private fun jsonb(value: String): PGobject = PGobject().apply {
        type = "jsonb"
        this.value = value
    }

    private fun encodeHeaders(headers: Map<String, String>): String =
        objectMapper.writeValueAsString(headers)

    private fun decodeHeaders(value: String): Map<String, String> =
        objectMapper.readValue(value, HEADERS_TYPE)

    private data class ClaimedRow(
        val id: UUID,
        val entry: OutboxEntry,
        val retryCount: Int,
    )

    private companion object {

        val HEADERS_TYPE = object : TypeReference<Map<String, String>>() {}

        const val INSERT_WITH_NOW = """
            INSERT INTO message_outbox (
                id, topic, key, body, headers, created_at, published_at, state,
                lease_owner, lease_until, retry_count, next_attempt_at
            ) VALUES (
                :id, :topic, :key, :body, :headers, now(), NULL, 'NEW',
                NULL, NULL, 0, NULL
            )
        """

        const val INSERT_WITH_CREATED_AT = """
            INSERT INTO message_outbox (
                id, topic, key, body, headers, created_at, published_at, state,
                lease_owner, lease_until, retry_count, next_attempt_at
            ) VALUES (
                :id, :topic, :key, :body, :headers, :createdAt, NULL, 'NEW',
                NULL, NULL, 0, NULL
            )
        """

        const val CLAIM_SELECT = """
            SELECT id, topic, key, body, headers, created_at, retry_count
            FROM message_outbox
            WHERE state = 'NEW' AND created_at < :before
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        """

        const val CLAIM_UPDATE = """
            UPDATE message_outbox
            SET state = 'CLAIMED', lease_owner = :owner, lease_until = :leaseUntil
            WHERE id IN (:ids)
        """

        const val RELEASE_DUE_RETRIES = """
            UPDATE message_outbox
            SET state = 'NEW', next_attempt_at = NULL
            WHERE state = 'RETRY_WAIT' AND next_attempt_at <= :now
        """

        const val MARK_PUBLISHED = """
            UPDATE message_outbox
            SET state = 'PUBLISHED', published_at = now(), lease_owner = NULL, lease_until = NULL
            WHERE id IN (:ids)
        """

        const val MARK_RETRY = """
            UPDATE message_outbox
            SET state = 'RETRY_WAIT', next_attempt_at = :nextAttemptAt, retry_count = :retryCount,
                lease_owner = NULL, lease_until = NULL
            WHERE id = :id
        """

        const val MARK_DEAD = """
            UPDATE message_outbox
            SET state = 'DEAD', lease_owner = NULL, lease_until = NULL
            WHERE id = :id
        """

        const val RECOVER_EXPIRED_LEASES = """
            UPDATE message_outbox
            SET state = 'NEW', lease_owner = NULL, lease_until = NULL
            WHERE state = 'CLAIMED' AND lease_until < :now
        """

        const val DELETE_TERMINAL = """
            DELETE FROM message_outbox
            WHERE (state = 'PUBLISHED' AND published_at < :publishedBefore)
               OR (state = 'DEAD' AND created_at < :deadBefore)
        """

        const val COUNT_BY_STATE = """
            SELECT count(*) FROM message_outbox WHERE state = :state
        """

        const val COUNT_DUE_RETRIES = """
            SELECT count(*) FROM message_outbox
            WHERE state = 'RETRY_WAIT' AND next_attempt_at <= :now
        """

    }

}
