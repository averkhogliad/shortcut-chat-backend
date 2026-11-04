package io.averkhoglyad.shortcut.outbox.core

import io.averkhoglyad.shortcut.outbox.persistence.OutboxDao
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionManager
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate

class OutboxWriterImplTest : FreeSpec({

    val dao = mockk<OutboxDao>()
    val transactionManager = TestTransactionManager()
    val target: OutboxWriter = ProxyFactory(OutboxWriterImpl(dao)).apply {
        isProxyTargetClass = false
        addAdvice(TransactionInterceptor(transactionManager as TransactionManager, AnnotationTransactionAttributeSource()))
    }.proxy as OutboxWriter

    afterTest {
        clearAllMocks()
    }

    "write" - {
        "inserts the entry through the DAO inside an active transaction" {
            // given
            val entry = outboxEntry()
            every { dao.insert(any()) } just Runs

            // when
            TransactionTemplate(transactionManager).execute {
                target.write(entry)
            }

            // then
            val inserted = slot<OutboxEntry>()
            verify(exactly = 1) { dao.insert(capture(inserted)) }
            inserted.captured shouldBe entry
        }

        "fails and writes nothing without an active transaction" {
            // given
            val entry = outboxEntry()
            every { dao.insert(any()) } just Runs

            // when
            shouldThrow<IllegalTransactionStateException> {
                target.write(entry)
            }

            // then
            verify(exactly = 0) { dao.insert(any()) }
        }
    }
})

private class TestTransactionManager : AbstractPlatformTransactionManager() {

    private val active = ThreadLocal.withInitial { false }

    override fun doGetTransaction(): Any = Any()

    override fun isExistingTransaction(transaction: Any): Boolean = active.get()

    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        active.set(true)
    }

    override fun doCommit(status: DefaultTransactionStatus) = Unit

    override fun doRollback(status: DefaultTransactionStatus) = Unit

    override fun doCleanupAfterCompletion(transaction: Any) {
        active.remove()
    }

}
