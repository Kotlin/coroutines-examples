package channel

import java.util.*
import java.util.concurrent.atomic.*
import java.util.concurrent.locks.*
import kotlin.coroutines.*

interface SendChannel<T> {
    suspend fun send(value: T)
    fun close()
    fun <R> selectSend(a: SendCase<T, R>): Boolean
}

interface ReceiveChannel<T> {
    suspend fun receive(): T // throws NoSuchElementException on closed channel
    suspend fun receiveOrNull(): T? // returns null on closed channel
    fun <R> selectReceive(a: ReceiveCase<T, R>): Boolean
    suspend operator fun iterator(): ReceiveIterator<T>
}

interface ReceiveIterator<out T> {
    suspend operator fun hasNext(): Boolean
    suspend operator fun next(): T
}

private const val CHANNEL_CLOSED = "Channel was closed"

private val channelCounter = AtomicLong() // number channels for debugging

class Channel<T>(val capacity: Int = 1) : SendChannel<T>, ReceiveChannel<T> {
    init { require(capacity >= 1) }
    private val number = channelCounter.incrementAndGet() // for debugging
    private var closed = false
    private val buffer = ArrayDeque<T>(capacity)
    private val waiters = SentinelWaiter<T>()

    private val empty: Boolean get() = buffer.isEmpty()
    private val full: Boolean get() = buffer.size == capacity

    suspend override fun send(value: T): Unit = suspendCoroutine sc@ { c ->
        var receiveWaiter: Waiter<T>? = null
        locked {
            check(!closed) { CHANNEL_CLOSED }
            if (full) {
                addWaiter(SendWaiter(c, value))
                return@sc // suspended
            } else {
                receiveWaiter = unlinkFirstWaiter()
                if (receiveWaiter == null) {
                    buffer.add(value)
                }
            }
        }
        receiveWaiter?.resumeReceive(value)
        c.resume(Unit) // sent -> resume this coroutine right away
    }

    override fun <R> selectSend(a: SendCase<T, R>): Boolean {
        var receiveWaiter: Waiter<T>? = null
        locked {
            if (a.selector.resolved) return true // already resolved selector, do nothing
            check(!closed) { CHANNEL_CLOSED }
            if (full) {
                addWaiter(a)
                return false // suspended
            } else {
                receiveWaiter = unlinkFirstWaiter()
                if (receiveWaiter == null) {
                    buffer.add(a.value)
                }
            }
            a.unlink() // was resolved
        }
        receiveWaiter?.resumeReceive(a.value)
        a.resumeSend() // sent -> resume this coroutine right away
        return true
    }

    @Suppress("UNCHECKED_CAST")
    suspend override fun receive(): T = suspendCoroutine sc@ { c ->
        var sendWaiter: Waiter<T>? = null
        var wasClosed = false
        var result: T? = null
        locked {
            if (empty) {
                if (closed) {
                    wasClosed = true
                } else {
                    addWaiter(ReceiveWaiter(c))
                    return@sc // suspended
                }
            } else {
                result = buffer.removeFirst()
                sendWaiter = unlinkFirstWaiter()
                if (sendWaiter != null) buffer.add(sendWaiter!!.getSendValue())
            }
        }
        sendWaiter?.resumeSend()
        if (wasClosed)
            c.resumeWithException(NoSuchElementException(CHANNEL_CLOSED))
        else
            c.resume(result as T)
    }

    suspend override fun receiveOrNull(): T? = suspendCoroutine sc@ { c ->
        var sendWaiter: Waiter<T>? = null
        var result: T? = null
        locked {
            if (empty) {
                if (!closed) {
                    addWaiter(ReceiveOrNullWaiter(c))
                    return@sc // suspended
                }
            } else {
                result = buffer.removeFirst()
                sendWaiter = unlinkFirstWaiter()
                if (sendWaiter != null) buffer.add(sendWaiter!!.getSendValue())
            }
        }
        sendWaiter?.resumeSend()
        c.resume(result)
    }

    override fun <R> selectReceive(a: ReceiveCase<T, R>): Boolean {
        var sendWaiter: Waiter<T>? = null
        var wasClosed = false
        var result: T? = null
        locked {
            if (a.selector.resolved) return true // already resolved selector, do nothing
            if (empty) {
                if (closed) {
                    wasClosed = true
                } else {
                    addWaiter(a)
                    return false // suspended
                }
            } else {
                result = buffer.removeFirst()
                sendWaiter = unlinkFirstWaiter()
                if (sendWaiter != null) buffer.add(sendWaiter!!.getSendValue())
            }
            a.unlink() // was resolved
        }
        sendWaiter?.resumeSend()
        if (wasClosed)
            a.resumeClosed()
        else
            @Suppress("UNCHECKED_CAST")
            a.resumeReceive(result as T)
        return true
    }

    suspend override fun iterator(): ReceiveIterator<T> = ReceiveIteratorImpl()

    inner class ReceiveIteratorImpl: ReceiveIterator<T> {
        private var computedNext = false
        private var hasNextValue = false
        private var nextValue: T? = null

        suspend override fun hasNext(): Boolean {
            if (computedNext) return hasNextValue
            return suspendCoroutine sc@ { c ->
                var sendWaiter: Waiter<T>? = null
                locked {
                    if (empty) {
                        if (!closed) {
                            addWaiter(IteratorHasNextWaiter(c, this))
                            return@sc // suspended
                        } else
                            setClosed()
                    } else {
                        setNext(buffer.removeFirst())
                        sendWaiter = unlinkFirstWaiter()
                        if (sendWaiter != null) buffer.add(sendWaiter!!.getSendValue())
                    }
                }
                sendWaiter?.resumeSend()
                c.resume(hasNextValue)
            }
        }

        suspend override fun next(): T {
            // return value previous acquired by hasNext
            if (computedNext) {
                @Suppress("UNCHECKED_CAST")
                val result = nextValue as T
                computedNext = false
                nextValue = null
                return result
            }
            // do a regular receive is hasNext was not previously invoked
            return receive()
        }

        fun setNext(value: T) {
            computedNext = true
            hasNextValue = true
            nextValue = value
        }

        fun setClosed() {
            computedNext = true
            hasNextValue = false
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun close() {
        var killList: ArrayList<Waiter<T>>? = null
        locked {
            if (closed) return // ignore repeated close
            closed = true
            if (empty || full) {
                killList = arrayListOf()
                while (true) {
                    killList!!.add(unlinkFirstWaiter() ?: break)
                }
            } else {
                check (!hasWaiters) { "Channel with butter not-full and not-empty shall not have waiters"}
                return // nothing to do
            }
        }
        for (kill in killList!!) {
            kill.resumeClosed()
        }
    }

    private val hasWaiters: Boolean get() = waiters.next != waiters

    private fun addWaiter(w: Waiter<T>) {
        val last = waiters.prev!!
        w.prev = last
        w.next = waiters
        last.next = w
        waiters.prev = w
    }

    private fun unlinkFirstWaiter(): Waiter<T>? {
        val first = waiters.next!!
        if (first == waiters) return null
        first.unlink()
        return first
    }

    // debugging
    private val waitersString: String get() {
        val sb = StringBuilder("[")
        var w = waiters.next!!
        while (w != waiters) {
            if (sb.length > 1) sb.append(", ")
            sb.append(w)
            w = w.next!!
        }
        sb.append("]")
        return sb.toString()
    }

    override fun toString(): String = locked {
        "Channel #$number closed=$closed, buffer=$buffer, waiters=$waitersString"
    }
}

// This lock is used only for a short time to manage wait lists, no user code runs under it
private val lock = ReentrantLock()

private inline fun <R> locked(block: () -> R): R {
    lock.lock()
    return try { block() } finally { lock.unlock() }
}

sealed class Waiter<T> {
    var next: Waiter<T>? = null
    var prev: Waiter<T>? = null

    open fun resumeReceive(value: T) { throw IllegalStateException() }
    open fun resumeClosed() { throw IllegalStateException() }
    open fun getSendValue(): T { throw IllegalStateException() }
    open fun resumeSend() { throw IllegalStateException() }

    val linked: Boolean get() = next != null

    open fun unlink() { unlinkOne() }

    fun unlinkOne() {
        val prev = this.prev!!
        val next = this.next!!
        prev.next = next
        next.prev = prev
        this.prev = null
        this.next = null
    }

    // debug
    override fun toString(): String = "${super.toString()} linked=$linked"
}

class SentinelWaiter<T> : Waiter<T>() {
    init {
        prev = this
        next = this
    }

    override fun unlink() { throw IllegalStateException() }
}

class SendWaiter<T>(val c: Continuation<Unit>, val value: T) : Waiter<T>() {
    override fun getSendValue(): T = value
    override fun resumeSend() = c.resume(Unit)
    override fun resumeClosed() = c.resumeWithException(IllegalStateException(CHANNEL_CLOSED))
}

class ReceiveWaiter<T>(val c: Continuation<T>) : Waiter<T>() {
    override fun resumeReceive(value: T) = c.resume(value)
    override fun resumeClosed() = c.resumeWithException(NoSuchElementException(CHANNEL_CLOSED))
}

class ReceiveOrNullWaiter<T>(val c: Continuation<T?>) : Waiter<T>() {
    override fun resumeReceive(value: T) = c.resume(value)
    override fun resumeClosed() = c.resume(null)
}

class IteratorHasNextWaiter<T>(val c: Continuation<Boolean>, val it: Channel<T>.ReceiveIteratorImpl) : Waiter<T>() {
    override fun resumeReceive(value: T) { it.setNext(value); c.resume(true) }
    override fun resumeClosed() { it.setClosed(); c.resume(false) }
}

data class Selector<R>(val c: Continuation<R>, val cases: List<SelectCase<*, R>>) {
    var resolved = false

    fun resolve() {
        resolved = true
        cases
            .asSequence()
            .filter { it.linked }
            .forEach { it.unlinkOne() }
    }
}

sealed class SelectCase<T, R> : Waiter<T>() {
    lateinit var selector: Selector<R>
    abstract fun select(selector: Selector<R>): Boolean

    override fun unlink() {
        selector.resolve()
    }
}

class SendCase<T, R>(val c: SendChannel<T>, val value: T, val action: () -> R) : SelectCase<T, R>() {
    override fun getSendValue(): T = value
    override fun resumeSend() = selector.c.resume(action())
    override fun resumeClosed() = selector.c.resumeWithException(IllegalStateException(CHANNEL_CLOSED))
    override fun select(selector: Selector<R>): Boolean = c.selectSend(this)
}

class ReceiveCase<T, R>(val c: ReceiveChannel<T>, val action: (T) -> R) : SelectCase<T, R>() {
    override fun resumeReceive(value: T) = selector.c.resume(action(value))
    override fun resumeClosed() = selector.c.resumeWithException(NoSuchElementException(CHANNEL_CLOSED))
    override fun select(selector: Selector<R>): Boolean = c.selectReceive(this)
}

class DefaultCase<R>(val action: suspend () -> R) : SelectCase<Nothing, R>() {
    override fun select(selector: Selector<R>): Boolean {
        locked {
            if (selector.resolved) return true // already resolved selector, do nothing
            selector.resolve() // default case resolves selector immediately
        }
        // now start action
        action.startCoroutine(completion = selector.c)
        return true
    }
}
