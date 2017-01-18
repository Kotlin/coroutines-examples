package run

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

fun launch(context: CoroutineContext, block: suspend () -> Unit) =
        block.startCoroutine(StandaloneCoroutine(context))

private class StandaloneCoroutine(override val context: CoroutineContext): Continuation<Unit> {
    override fun resume(value: Unit) {}

    override fun resumeWithException(exception: Throwable) {
        val currentThread = Thread.currentThread()
        currentThread.uncaughtExceptionHandler.uncaughtException(currentThread, exception)
    }
}
