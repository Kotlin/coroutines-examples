import java.util.concurrent.CompletableFuture
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

suspend fun <T> await(f: CompletableFuture<T>): T =
    suspendCoroutine<T> { c: Continuation<T> ->
        f.whenComplete { result, exception ->
            if (exception == null)
            // the future has been completed normally, resume execution with result
                c.resume(result)
            else
            // the future has completed with an exception, resume execution with exception
                c.resumeWithException(exception)
        }
    }
