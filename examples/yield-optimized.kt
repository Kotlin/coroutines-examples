import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineIntrinsics
import kotlin.coroutines.createCoroutine

fun <T> generateOptimized(block: suspend Generator<T>.() -> Unit): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> {
        val iterator = GeneratorIteratorOptimized<T>()
        val initial = block.createCoroutine(iterator, iterator)
        iterator.setNextStep(initial)
        return iterator
    }
}

class GeneratorIteratorOptimized<T>: AbstractIterator<T>(), Generator<T>, Continuation<Unit> {
    private lateinit var nextStep: Continuation<Unit>

    fun setNextStep(step: Continuation<Unit>) { this.nextStep = step }

    // AbstractIterator implementation
    override fun computeNext() { nextStep.resume(Unit) }

    // Completion continuation implementation
    override fun resume(value: Unit) { done() }
    override fun resumeWithException(exception: Throwable) { throw exception }

    // Generator implementation
    override suspend fun yield(value: T) {
        setNext(value)
        return CoroutineIntrinsics.suspendCoroutineOrReturn { c ->
            setNextStep(c)
            CoroutineIntrinsics.SUSPENDED
        }
    }
}
