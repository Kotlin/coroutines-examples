package channel

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationDispatcher
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

object go {
    private val coroutineCounter = AtomicLong() // number coroutines for debugging
    private val active = ConcurrentHashMap<Long, CoroutineRef>()

    private val maxThreads = Integer.getInteger("maxThreads", Runtime.getRuntime().availableProcessors())

    private val pool = ScheduledThreadPoolExecutor(maxThreads, ThreadFactory { runnable ->
        val thread = Thread(runnable)
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e ->
            e.printStackTrace()
        }
        thread
    })

    init {
        pool.setKeepAliveTime(1, TimeUnit.MILLISECONDS)
        pool.allowCoreThreadTimeOut(true)
        pool.maximumPoolSize = maxThreads
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                val asleep = active.values.filter { !it.daemon }
                if (asleep.any { it.main })
                    println("fatal: $asleep asleep -- deadlock")
            }
        })
    }

    suspend fun sleep(millis: Long): Unit = suspendCoroutine { c ->
        pool.schedule({ c.resume(Unit)}, millis, TimeUnit.MILLISECONDS)
    }

    // don't warn about "daemon" coroutines on shutdown
    operator fun invoke(name: String? = null, daemon: Boolean = false, main: Boolean = false, block: suspend go.() -> Unit) {
        val ref = CoroutineRef(coroutineCounter.incrementAndGet(), name, daemon, main)
        block.startCoroutine(receiver = go, completion = ref, dispatcher = ref)
    }

    fun main(block: suspend go.() -> Unit) = invoke(main = true, block = block)

    private class CoroutineRef(
        val number: Long,
        val name: String?,
        val daemon: Boolean,
        val main: Boolean
    ) : Continuation<Unit>, ContinuationDispatcher {
        init {
            active.put(number, this)
        }

        override fun resume(value: Unit) {
            done()
        }

        override fun resumeWithException(exception: Throwable) {
            done()
            throw exception
        }

        private fun done() {
            active.remove(number)
        }

        override fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean {
            pool.execute { continuation.resume(value) }
            return true
        }

        override fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean {
            pool.execute { continuation.resumeWithException(exception) }
            return true
        }

        override fun toString(): String =
            "coroutine #$number" +
                (if (name != null) " '$name'" else "") +
                (if (daemon) "-daemon" else "") +
                (if (main) "-main" else "")
    }
}
