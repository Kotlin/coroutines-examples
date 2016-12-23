import java.util.concurrent.CompletableFuture
import javax.swing.SwingUtilities
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationDispatcher
import kotlin.coroutines.startCoroutine

object Swing : ContinuationDispatcher {
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
