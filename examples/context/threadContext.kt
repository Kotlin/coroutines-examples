package context

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor

fun newFixedThreadPoolContext(nThreads: Int, name: String) = ThreadContext(nThreads, name)
fun newSingleThreadContext(name: String) = ThreadContext(1, name)

private val thisThreadContext = ThreadLocal<ThreadContext>()

class ThreadContext(
    nThreads: Int,
    name: String
) : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
    val threadNo = AtomicInteger()
    val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(nThreads) { target ->
        thread(start = false, isDaemon = true, name = name + "-" + threadNo.incrementAndGet()) {
            thisThreadContext.set(this@ThreadContext)
            target.run()
        }
    }

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
            ThreadContinuation(continuation.context.fold(continuation, { cont, element ->
                if (element != this@ThreadContext && element is ContinuationInterceptor)
                    element.interceptContinuation(cont) else cont
            }))

    private inner class ThreadContinuation<T>(val continuation: Continuation<T>) : Continuation<T> by continuation {
        override fun resume(value: T) {
            if (isContextThread()) continuation.resume(value)
            else executor.execute { continuation.resume(value) }
        }

        override fun resumeWithException(exception: Throwable) {
            if (isContextThread()) continuation.resumeWithException(exception)
            else executor.execute { continuation.resumeWithException(exception) }
        }
    }

    private fun isContextThread() = thisThreadContext.get() == this@ThreadContext
}

