package sequence.optimized

import sequence.SequenceBuilder
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.experimental.intrinsics.createCoroutineUnchecked
import kotlin.coroutines.experimental.intrinsics.suspendCoroutineOrReturn

fun <T> buildSequence(block: suspend SequenceBuilder<T>.() -> Unit): Sequence<T> = Sequence {
    SequenceCoroutine<T>().apply {
        nextStep = block.createCoroutineUnchecked(receiver = this, completion = this)
    }
}

class SequenceCoroutine<T>: AbstractIterator<T>(), SequenceBuilder<T>, Continuation<Unit> {
    lateinit var nextStep: Continuation<Unit>

    // AbstractIterator implementation
    override fun computeNext() { nextStep.resume(Unit) }

    // Completion continuation implementation
    override val context: CoroutineContext get() = EmptyCoroutineContext
    override fun resume(value: Unit) { done() }
    override fun resumeWithException(exception: Throwable) { throw exception }

    // Generator implementation
    override suspend fun yield(value: T) {
        setNext(value)
        return suspendCoroutineOrReturn { cont ->
            nextStep = cont
            COROUTINE_SUSPENDED
        }
    }
}
