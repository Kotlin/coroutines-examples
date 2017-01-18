package run

import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

fun <T> runBlocking(context: CoroutineContext, block: suspend () -> T): T =
        BlockingCoroutine<T>(context).also { block.startCoroutine(it) }.getValue()

private class BlockingCoroutine<T>(override val context: CoroutineContext): Continuation<T> {
    private val lock = ReentrantLock()
    private val done = lock.newCondition()
    private var completed = false
    private var value: T? = null
    private var exception: Throwable? = null

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        return try { block() }
                finally { lock.unlock() }
    }

    override fun resume(value: T) = locked {
        this.value = value
        completed = true
        done.signal()
    }

    override fun resumeWithException(exception: Throwable) = locked {
        this.exception = exception
        completed = true
        done.signal()
    }

    fun getValue(): T = locked {
        while (!completed) done.awaitUninterruptibly()
        exception?.let { throw it }
        value as T
    }
}
