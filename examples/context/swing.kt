package context

import javax.swing.SwingUtilities
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor

object Swing : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
        SwingContinuation(continuation.context.fold(continuation, { cont, element ->
            if (element != Swing && element is ContinuationInterceptor)
                element.interceptContinuation(cont) else cont
        }))
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
