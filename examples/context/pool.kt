package context

import run.*
import java.util.concurrent.*
import kotlin.coroutines.*

object CommonPool : Pool(ForkJoinPool.commonPool())

open class Pool(val pool: ForkJoinPool) : AbstractCoroutineContextElement(ContinuationInterceptor),
    ContinuationInterceptor {
    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
        PoolContinuation(pool, continuation.context.fold(continuation) { cont, element ->
            if (element != this@Pool && element is ContinuationInterceptor)
                element.interceptContinuation(cont) else cont
        })

    // runs new coroutine in this pool in parallel (schedule to a different thread)
    fun runParallel(block: suspend () -> Unit) {
        pool.execute { launch(this, block) }
    }
}

private class PoolContinuation<T>(
    val pool: ForkJoinPool,
    val cont: Continuation<T>
) : Continuation<T> {
    override val context: CoroutineContext = cont.context

    override fun resumeWith(result: Result<T>) {
        pool.execute { cont.resumeWith(result) }
    }
}
