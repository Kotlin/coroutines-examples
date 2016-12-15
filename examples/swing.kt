import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationDispatcher
import kotlin.coroutines.startCoroutine

object SwingDispatcher : ContinuationDispatcher {
    override fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean {
        if (SwingUtilities.isEventDispatchThread()) return false
        SwingUtilities.invokeLater { continuation.resume(value) }
        return true
    }

    override fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean {
        if (SwingUtilities.isEventDispatchThread()) return false
        SwingUtilities.invokeLater { continuation.resumeWithException(exception) }
        return true
    }
}

fun <T> asyncSwing(block: suspend () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    block.startCoroutine(completion=object : Continuation<T> {
        override fun resume(value: T) {
            future.complete(value)
        }
        override fun resumeWithException(exception: Throwable) {
            future.completeExceptionally(exception)
        }
    }, dispatcher=SwingDispatcher) // Note the dispatcher parameter to startCoroutine
    return future
}
