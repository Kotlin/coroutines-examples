package future

import context.*
import java.util.concurrent.*
import kotlin.coroutines.*

fun <T> future(context: CoroutineContext = CommonPool, block: suspend () -> T): CompletableFuture<T> =
        CompletableFutureCoroutine<T>(context).also { block.startCoroutine(completion = it) }

class CompletableFutureCoroutine<T>(override val context: CoroutineContext) : CompletableFuture<T>(), Continuation<T> {
    override fun resumeWith(result: SuccessOrFailure<T>) {
        if(result.isSuccess) {
            complete(result.getOrNull())
        }else {
            completeExceptionally(result.exceptionOrNull())
        }
    }
}
