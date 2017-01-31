package context

import javax.swing.SwingUtilities
import kotlin.coroutines.experimental.AbstractCoroutineContextElement
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.ContinuationInterceptor

object Swing : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
        SwingContinuation(continuation)
}

private class SwingContinuation<T>(val cont: Continuation<T>) : Continuation<T> by cont {
    override fun resume(value: T) {
        if (SwingUtilities.isEventDispatchThread()) cont.resume(value)
        else SwingUtilities.invokeLater { cont.resume(value) }
    }

    override fun resumeWithException(exception: Throwable) {
        if (SwingUtilities.isEventDispatchThread()) cont.resumeWithException(exception)
        else SwingUtilities.invokeLater { cont.resumeWithException(exception) }
    }
}
