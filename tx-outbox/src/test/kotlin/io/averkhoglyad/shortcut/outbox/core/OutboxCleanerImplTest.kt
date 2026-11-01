package io.averkhoglyad.shortcut.outbox.core

import io.averkhoglyad.shortcut.outbox.persistence.OutboxDao
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class OutboxCleanerImplTest : FreeSpec({

    val dao = mockk<OutboxDao>()
    val now = Instant.parse("2026-01-01T00:00:00Z")
    val clock = Clock.fixed(now, ZoneOffset.UTC)
    val config = OutboxCleanerConfig(
        publishedRetention = Duration.ofDays(7),
        deadRetention = Duration.ofDays(30),
    )
    val target = OutboxCleanerImpl(dao, config, clock)

    beforeTest {
        every { dao.deleteTerminal(any(), any()) } just Runs
    }

    afterTest {
        clearAllMocks()
    }

    "clean" - {
        "deletes terminal records by the configured retentions" {
            // when
            target.clean()

            // then
            val publishedBefore = slot<Instant>()
            val deadBefore = slot<Instant>()
            verify(exactly = 1) { dao.deleteTerminal(capture(publishedBefore), capture(deadBefore)) }
            publishedBefore.captured shouldBe now.minus(config.publishedRetention)
            deadBefore.captured shouldBe now.minus(config.deadRetention)
        }
    }
})
