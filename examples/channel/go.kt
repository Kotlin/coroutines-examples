package channel

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.*

object Go {
    private val number = AtomicInteger()
    private val active = ConcurrentHashMap<Int, CoroutineRef>()

    private val maxThreads = Runtime.getRuntime().availableProcessors()

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
                val asleep = active.values.filter { !it.forever }
                if (!asleep.isEmpty())
                    println("fatal error: $asleep asleep - deadlock!")
            }
        })
    }

    suspend fun sleep(millis: Long): Unit = suspendCoroutine { c ->
        pool.schedule({ c.resume(Unit)}, millis, TimeUnit.MILLISECONDS)
    }

    // don't warn about "forever" coroutines on shutdown
    fun go(name: String? = null, forever: Boolean = false, block: suspend Go.() -> Unit) {
        val ref = CoroutineRef(number.incrementAndGet(), name, forever)
        block.startCoroutine(receiver = Go, completion = ref, dispatcher = ref)
    }

    private class CoroutineRef(
        val index: Int, val name: String?, val forever: Boolean
    ) : Continuation<Unit>, ContinuationDispatcher {
        init {
            active.put(index, this)
        }

        override fun resume(value: Unit) {
            done()
        }

        override fun resumeWithException(exception: Throwable) {
            done()
            throw exception
        }

        private fun done() {
            active.remove(index)
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
            "coroutine #$index${if (name != null) " '$name'" else ""}${if (forever) " ,forever" else ""}"
    }
}

suspend fun <T> suspending(block: suspend Go.() -> T): T = CoroutineIntrinsics.suspendCoroutineOrReturn { c ->
    block.startCoroutine(receiver = Go, completion = c)
    CoroutineIntrinsics.SUSPENDED
}

fun go(name: String? = null, forever: Boolean = false, block: suspend Go.() -> Unit) = Go.go(name, forever, block)
