package context

import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.concurrent.*
import kotlin.coroutines.*

fun newFixedThreadPoolContext(nThreads: Int, name: String) = ThreadContext(nThreads, name)
fun newSingleThreadContext(name: String) = ThreadContext(1, name)

class ThreadContext(
    nThreads: Int,
    name: String
) : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
    val threadNo = AtomicInteger()
    
    val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(nThreads) { target ->
        thread(start = false, isDaemon = true, name = name + "-" + threadNo.incrementAndGet()) {
            target.run()
        }
    }

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
        ThreadContinuation(continuation.context.fold(continuation) { cont, element ->
            if (element != this@ThreadContext && element is ContinuationInterceptor)
                element.interceptContinuation(cont) else cont
        })

    private inner class ThreadContinuation<T>(val cont: Continuation<T>) : Continuation<T>{
        override val context: CoroutineContext = cont.context

        override fun resumeWith(result: Result<T>) {
            executor.execute { cont.resumeWith(result) }
        }
    }
}

