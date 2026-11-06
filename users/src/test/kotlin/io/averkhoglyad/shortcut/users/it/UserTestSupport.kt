package io.averkhoglyad.shortcut.users.it

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import java.util.UUID

/**
 * User-row and outbox fixtures shared by the `users` endpoint specs, plus the mock JWT used to
 * authenticate a request as the user identified by its `sub` claim.
 */
internal fun jwtFor(userId: UUID) = jwt().jwt { it.subject(userId.toString()) }

internal fun JdbcClient.findUserRow(id: UUID): Map<String, Any?>? =
    this.sql("select * from users where id=:id")
        .param("id", id)
        .query()
        .listOfRows()
        .firstOrNull()

internal fun JdbcClient.countOutboxRecords(): Long =
    this.sql("select count(*) from message_outbox")
        .query(Long::class.java)
        .single()

internal fun JdbcClient.findOutboxRecordByEventName(eventName: String): Map<String, Any?>? =
    this.sql("select * from message_outbox where headers->>'X-Event-Name' = :eventName")
        .param("eventName", eventName)
        .query()
        .listOfRows()
        .firstOrNull()
