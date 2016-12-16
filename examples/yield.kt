import kotlin.coroutines.Continuation
import kotlin.coroutines.RestrictsSuspendExtensions
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.suspendCoroutine

@RestrictsSuspendExtensions
interface Generator<T> {
    suspend fun yield(value: T)
}

fun <T> generate(block: suspend Generator<T>.() -> Unit): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> {
        val iterator = GeneratorIterator<T>()
        val initial = block.createCoroutine(receiver = iterator, completion = iterator)
        iterator.setNextStep(initial)
        return iterator
    }
}

class GeneratorIterator<T>: AbstractIterator<T>(), Generator<T>, Continuation<Unit> {
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
        return suspendCoroutine { c -> setNextStep(c) }
    }
}
