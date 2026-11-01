package io.averkhoglyad.shortcut.common.persistence.transaction

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import org.springframework.core.Ordered
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED
import org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK
import org.springframework.transaction.support.TransactionSynchronizationManager

class SynchronizationTest : FreeSpec({

    beforeTest {
        TransactionSynchronizationManager.initSynchronization()
    }

    afterTest {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    "transaction" - {
        "runs afterCommit callback on commit" {
            // given
            var calls = 0
            transaction { afterCommit { calls++ } }
            val synchronization = singleSynchronization()

            // when
            synchronization.afterCommit()

            // then
            calls shouldBe 1
        }

        "runs afterSuccess callback on committed completion" {
            // given
            var calls = 0
            transaction { afterSuccess { calls++ } }
            val synchronization = singleSynchronization()

            // when
            synchronization.afterCompletion(STATUS_COMMITTED)

            // then
            calls shouldBe 1
        }

        "runs afterRollBack callback on rolled back completion" {
            // given
            var calls = 0
            transaction { afterRollBack { calls++ } }
            val synchronization = singleSynchronization()

            // when
            synchronization.afterCompletion(STATUS_ROLLED_BACK)

            // then
            calls shouldBe 1
        }

        "passes readOnly flag to beforeCommit callback" {
            // given
            var readOnly: Boolean? = null
            transaction { beforeCommit { flag -> readOnly = flag } }
            val synchronization = singleSynchronization()

            // when
            synchronization.beforeCommit(true)

            // then
            readOnly shouldBe true
        }

        "passes completion status to afterCompletion callback" {
            // given
            var status: Int? = null
            transaction { afterCompletion { value -> status = value } }
            val synchronization = singleSynchronization()

            // when
            synchronization.afterCompletion(STATUS_ROLLED_BACK)

            // then
            status shouldBe STATUS_ROLLED_BACK
        }

        "applies explicit order" {
            // given
            transaction { order(42) }

            // when
            val order = (singleSynchronization() as Ordered).order

            // then
            order shouldBe 42
        }

        "applies computed order" {
            // given
            transaction { order { 7 } }

            // when
            val order = (singleSynchronization() as Ordered).order

            // then
            order shouldBe 7
        }
    }
})

private fun singleSynchronization(): TransactionSynchronization =
    TransactionSynchronizationManager.getSynchronizations().single()
