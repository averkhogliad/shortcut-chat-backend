package io.averkhoglyad.shortcut.users.core.persistence.repository

import io.averkhoglyad.shortcut.common.test.executeSql
import io.averkhoglyad.shortcut.users.TestcontainersConfiguration
import io.averkhoglyad.shortcut.users.config.PersistenceConfig
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Pageable
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.time.Period
import java.util.*

private const val INIT = "/repository/outbox/init.sql"
private const val CLEAR = "/repository/outbox/clear.sql"

private val firstPendingId = UUID.fromString("20000000-0000-0000-0000-000000000001")
private val secondPendingId = UUID.fromString("20000000-0000-0000-0000-000000000002")
private val publishedId = UUID.fromString("20000000-0000-0000-0000-000000000003")

@DataJdbcTest
@AutoConfigureTestDatabase(replace = NONE)
@Import(TestcontainersConfiguration::class, PersistenceConfig::class)
class OutboxMessageRepositoryTest(
    private val target: OutboxMessageRepository,
    private val jdbc: JdbcClient,
) : FreeSpec({

    beforeEach { executeSql(INIT) }
    afterEach { executeSql(CLEAR) }

    "findByCreatedAtLessThanAndPublishedAtIsNullOrderByCreatedAt" - {
        "returns pending messages in creation order" {
            // when
            val result = target.findByCreatedAtLessThanAndPublishedAtIsNullOrderByCreatedAt(
                Instant.now(),
                Pageable.ofSize(10),
            )

            // then
            result shouldHaveSize 2
            result.map { it.id } shouldBe listOf(firstPendingId, secondPendingId)
        }

        "returns empty list when nothing was created before threshold" {
            // when
            val result = target.findByCreatedAtLessThanAndPublishedAtIsNullOrderByCreatedAt(
                Instant.now().minus(Period.ofDays(10)),
                Pageable.ofSize(10),
            )

            // then
            result.shouldBeEmpty()
        }

        "does not return messages outside requested portion" {
            // when
            val result = target.findByCreatedAtLessThanAndPublishedAtIsNullOrderByCreatedAt(
                Instant.now(),
                Pageable.ofSize(1),
            )

            // then
            result shouldHaveSize 1
            result.single().id shouldBe firstPendingId
        }
    }

    "markAsPublishedByIdIsIn" - {
        "marks requested messages as published" {
            // when
            target.markAsPublishedByIdIsIn(listOf(firstPendingId, secondPendingId))

            // then
            val pending = target.findByCreatedAtLessThanAndPublishedAtIsNullOrderByCreatedAt(
                Instant.now(),
                Pageable.ofSize(10),
            )
            pending.shouldBeEmpty()

            jdbc.publishedAt(firstPendingId).shouldNotBeNull()
            jdbc.publishedAt(secondPendingId).shouldNotBeNull()
            jdbc.publishedAt(publishedId).shouldNotBeNull()
        }
    }

    "deleteByPublishedAtLessThan" - {
        "deletes published messages before threshold" {
            // when
            target.deleteByPublishedAtLessThan(Instant.now().minus(java.time.Duration.ofHours(12)))

            // then
            jdbc.countOutbox() shouldBe 2L
            jdbc.publishedAt(publishedId) shouldBe null
        }
    }
})

private fun JdbcClient.countOutbox(): Long =
    this.sql("select count(*) from message_outbox")
        .query(Long::class.java)
        .single()

private fun JdbcClient.publishedAt(id: UUID): Any? =
    this.sql("select published_at from message_outbox where id=:id")
        .param("id", id)
        .query(Any::class.java)
        .optional()
        .orElse(null)
