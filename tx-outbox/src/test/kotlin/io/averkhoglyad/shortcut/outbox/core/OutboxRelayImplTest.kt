package io.averkhoglyad.shortcut.outbox.core

import io.averkhoglyad.shortcut.outbox.messaging.OutboxPublisher
import io.averkhoglyad.shortcut.outbox.messaging.PublishResult
import io.averkhoglyad.shortcut.outbox.persistence.ClaimedOutboxEntry
import io.averkhoglyad.shortcut.outbox.persistence.OutboxDao
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.uuid
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class OutboxRelayImplTest : FreeSpec({

    val dao = mockk<OutboxDao>()
    val publisher = mockk<OutboxPublisher>()
    val now = Instant.parse("2026-01-01T00:00:00Z")
    val clock = Clock.fixed(now, ZoneOffset.UTC)
    val relayConfig = OutboxRelayConfig(batchSize = 2, maxLoopsPerRun = 3)
    val leaseConfig = OutboxLeaseConfig(owner = "relay-1", duration = Duration.ofSeconds(30))
    val retryPolicy = OutboxRetryPolicy(
        initialDelay = Duration.ofSeconds(5),
        multiplier = 2.0,
        maxDelay = Duration.ofMinutes(5),
        maxAttempts = 3,
    )
    val target = OutboxRelayImpl(dao, publisher, relayConfig, leaseConfig, retryPolicy, clock)

    beforeTest {
        every { dao.recoverExpiredLeases(any()) } just Runs
        every { dao.releaseDueRetries(any()) } just Runs
        every { dao.claimBatch(any(), any(), any(), any()) } returns emptyList()
    }

    afterTest {
        clearAllMocks()
    }

    "runCycle" - {
        "recovers expired leases, releases due retries, then claims a batch" {
            // when
            target.runCycle()

            // then
            verifyOrder {
                dao.recoverExpiredLeases(now)
                dao.releaseDueRetries(now)
                dao.claimBatch(
                    leaseConfig.owner,
                    now.plus(leaseConfig.duration),
                    now,
                    relayConfig.batchSize,
                )
            }
        }

        "marks claimed entries published after successful publish" {
            // given
            val first = outboxEntry(id = Arb.uuid().next())
            val second = outboxEntry(id = Arb.uuid().next())
            every { dao.claimBatch(any(), any(), any(), any()) } returnsMany listOf(
                listOf(ClaimedOutboxEntry(first, retryCount = 0), ClaimedOutboxEntry(second, retryCount = 1)),
                emptyList(),
            )
            every { publisher.publish(any()) } returns PublishResult.Success
            every { dao.markPublished(any()) } just Runs

            // when
            target.runCycle()

            // then
            val published = mutableListOf<List<UUID>>()
            verify(exactly = 1) { dao.markPublished(capture(published)) }
            published.single() shouldBe listOf(first.id, second.id)

            val sent = mutableListOf<io.averkhoglyad.shortcut.outbox.core.OutboxEntry>()
            verify(exactly = 2) { publisher.publish(capture(sent)) }
            sent shouldBe listOf(first, second)
        }

        "schedules a retry with exponential backoff when publish fails" {
            // given
            val entry = outboxEntry(id = Arb.uuid().next())
            every { dao.claimBatch(any(), any(), any(), any()) } returnsMany listOf(
                listOf(ClaimedOutboxEntry(entry, retryCount = 0)),
                emptyList(),
            )
            every { publisher.publish(any()) } returns PublishResult.Failure(RuntimeException("kafka down"))
            every { dao.markRetry(any(), any(), any()) } just Runs

            // when
            target.runCycle()

            // then
            val id = slot<UUID>()
            val nextAttemptAt = slot<Instant>()
            val retryCount = slot<Int>()
            verify(exactly = 1) { dao.markRetry(capture(id), capture(nextAttemptAt), capture(retryCount)) }
            id.captured shouldBe entry.id
            nextAttemptAt.captured shouldBe now.plusSeconds(5)
            retryCount.captured shouldBe 1
            verify(exactly = 0) { dao.markPublished(any()) }
        }

        "grows the retry delay by the multiplier for later attempts" {
            // given
            val entry = outboxEntry(id = Arb.uuid().next())
            every { dao.claimBatch(any(), any(), any(), any()) } returnsMany listOf(
                listOf(ClaimedOutboxEntry(entry, retryCount = 1)),
                emptyList(),
            )
            every { publisher.publish(any()) } returns PublishResult.Failure()
            every { dao.markRetry(any(), any(), any()) } just Runs

            // when
            target.runCycle()

            // then
            val nextAttemptAt = slot<Instant>()
            val retryCount = slot<Int>()
            verify(exactly = 1) { dao.markRetry(any(), capture(nextAttemptAt), capture(retryCount)) }
            nextAttemptAt.captured shouldBe now.plusSeconds(10)
            retryCount.captured shouldBe 2
        }

        "marks the entry dead when the retry limit is reached" {
            // given
            val entry = outboxEntry(id = Arb.uuid().next())
            every { dao.claimBatch(any(), any(), any(), any()) } returnsMany listOf(
                listOf(ClaimedOutboxEntry(entry, retryCount = 2)),
                emptyList(),
            )
            every { publisher.publish(any()) } returns PublishResult.Failure()
            every { dao.markDead(any()) } just Runs

            // when
            target.runCycle()

            // then
            val id = slot<UUID>()
            verify(exactly = 1) { dao.markDead(capture(id)) }
            id.captured shouldBe entry.id
            verify(exactly = 0) { dao.markRetry(any(), any(), any()) }
            verify(exactly = 0) { dao.markPublished(any()) }
        }

        "stops claiming once the configured loop limit is reached" {
            // given
            val entry = outboxEntry(id = Arb.uuid().next())
            every { dao.claimBatch(any(), any(), any(), any()) } returns listOf(ClaimedOutboxEntry(entry, retryCount = 0))
            every { publisher.publish(any()) } returns PublishResult.Success
            every { dao.markPublished(any()) } just Runs

            // when
            target.runCycle()

            // then
            verify(exactly = relayConfig.maxLoopsPerRun) { dao.claimBatch(any(), any(), any(), any()) }
        }
    }
})
