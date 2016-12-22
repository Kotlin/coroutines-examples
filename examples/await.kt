import java.util.concurrent.CompletableFuture
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

suspend fun <T> CompletableFuture<T>.await(): T =
    suspendCoroutine<T> { c: Continuation<T> ->
        whenComplete { result, exception ->
            if (exception == null) // the future has been completed normally
                c.resume(result)
            else // the future has completed with an exception
                c.resumeWithException(exception)
        }
    }
