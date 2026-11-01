package io.averkhoglyad.shortcut.chat.outbox

import io.averkhoglyad.shortcut.chat.core.persistence.repository.OutboxMessageRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.checkAll
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import java.time.Clock
import java.time.Instant

class MessageOutboxCleanerImplTest : FreeSpec({

    val repository = mockk<OutboxMessageRepository>()
    val clock = mockk<Clock>()

    afterTest {
        clearAllMocks()
    }

    "cleanOld" - {
        "deletes messages published before configured period" {
            checkAll(nowAndPeriodPairs) { (now, period) ->
                // given
                every { clock.instant() } returns now
                every { repository.deleteByPublishedAtLessThan(any()) } just Runs
                val target = MessageOutboxCleanerImpl(repository, period, clock)

                // when
                target.cleanOld()

                // then
                val threshold = slot<Instant>()
                verify(exactly = 1) { repository.deleteByPublishedAtLessThan(capture(threshold)) }
                threshold.captured shouldBe now.minus(period)

                clearAllMocks()
            }
        }
    }
})
