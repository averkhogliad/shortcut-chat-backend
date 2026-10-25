package io.averkhoglyad.shortcut.common.persistence.transaction

import org.springframework.core.Ordered
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization

private typealias Runnable = () -> Unit
private typealias Consumer<T> = (T) -> Unit

fun transaction(prepare: Synchronization.() -> Unit) {
    SynchronizationBuilder()
        .apply { prepare() }
        .build()
        .let { registerSynchronization(it) }
}

interface Synchronization {

    fun order(order: Int)
    fun order(cb: () -> Int)
    fun suspend(cb: Runnable)
    fun resume(cb: Runnable)
    fun flush(cb: Runnable)
    fun beforeCommit(cb: Runnable)
    fun beforeCommit(cb: Consumer<Boolean>)
    fun beforeCompletion(cb: Runnable)
    fun afterCommit(cb: Runnable)
    fun afterSuccess(cb: Runnable)
    fun afterRollBack(cb: Runnable)
    fun afterCompletion(cb: Runnable)
    fun afterCompletion(cb: Consumer<Int>)

}

private class SynchronizationBuilder : Synchronization {

    private var order = Ordered.LOWEST_PRECEDENCE
    private var suspend: Runnable? = null
    private var resume: Runnable? = null
    private var flush: Runnable? = null
    private var beforeCommit: Consumer<Boolean>? = null
    private var beforeCompletion: Runnable? = null
    private var afterCommit: Runnable? = null
    private var afterCompletion: Consumer<Int>? = null
    private var afterSuccess: Runnable? = null
    private var afterRollBack: Runnable? = null

    fun build(): TransactionSynchronization = TransactionSynchronizationImpl(
        order = this.order,
        suspendFn = this.suspend,
        resumeFn = this.resume,
        flushFn = this.flush,
        beforeCommitFn = this.beforeCommit,
        beforeCompletionFn = this.beforeCompletion,
        afterCommitFn = this.afterCommit,
        afterCompletionFn = this.afterCompletion,
        afterSuccessFn = this.afterSuccess,
        afterRollBackFn = this.afterRollBack
    )

    override fun order(cb: () -> Int) {
        order(cb())
    }

    override fun order(order: Int) {
        this.order = order
    }

    override fun suspend(cb: Runnable) {
        this.suspend = cb
    }

    override fun resume(cb: Runnable) {
        this.resume = cb
    }

    override fun flush(cb: Runnable) {
        this.flush = cb
    }

    override fun beforeCommit(cb: Runnable) {
        beforeCommit { _ -> cb() }
    }

    override fun beforeCommit(cb: Consumer<Boolean>) {
        this.beforeCommit = cb
    }

    override fun beforeCompletion(cb: Runnable) {
        this.beforeCompletion = cb
    }

    override fun afterCommit(cb: Runnable) {
        this.afterCommit = cb
    }

    override fun afterCompletion(cb: Runnable) {
        this.afterCompletion { _ -> cb() }
    }

    override fun afterCompletion(cb: Consumer<Int>) {
        this.afterCompletion = cb
    }

    override fun afterSuccess(cb: Runnable) {
        this.afterSuccess = cb
    }

    override fun afterRollBack(cb: Runnable) {
        this.afterRollBack = cb
    }
}

private class TransactionSynchronizationImpl(
    private val order: Int,
    private val suspendFn: Runnable?,
    private val resumeFn: Runnable?,
    private val flushFn: Runnable?,
    private val beforeCommitFn: Consumer<Boolean>?,
    private val beforeCompletionFn: Runnable?,
    private val afterCommitFn: Runnable?,
    private val afterCompletionFn: Consumer<Int>?,
    private val afterSuccessFn: Runnable?,
    private val afterRollBackFn: Runnable?
) : TransactionSynchronization, Ordered {

    override fun getOrder(): Int {
        return order
    }

    override fun suspend() {
        suspendFn?.invoke()
    }

    override fun resume() {
        resumeFn?.invoke()
    }

    override fun flush() {
        flushFn?.invoke()
    }

    override fun beforeCommit(readOnly: Boolean) {
        beforeCommitFn?.invoke(readOnly)
    }

    override fun beforeCompletion() {
        beforeCompletionFn?.invoke()
    }

    override fun afterCommit() {
        afterCommitFn?.invoke()
    }

    override fun afterCompletion(status: Int) {
        afterCompletionFn?.invoke(status)
        when (status) {
            TransactionSynchronization.STATUS_COMMITTED -> afterSuccessFn?.invoke()
            TransactionSynchronization.STATUS_ROLLED_BACK -> afterRollBackFn?.invoke()
        }
    }
}
