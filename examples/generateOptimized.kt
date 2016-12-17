package generateOptimized

import generate.Generator
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineIntrinsics
import kotlin.coroutines.createCoroutine

fun <T> generate(block: suspend Generator<T>.() -> Unit): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> {
        val iterator = GeneratorIterator<T>()
        iterator.nextStep = block.createCoroutine(receiver = iterator, completion = iterator)
        return iterator
    }
}

class GeneratorIterator<T>: AbstractIterator<T>(), Generator<T>, Continuation<Unit> {
    lateinit var nextStep: Continuation<Unit>

    // AbstractIterator implementation
    override fun computeNext() { nextStep.resume(Unit) }

    // Completion continuation implementation
    override fun resume(value: Unit) { done() }
    override fun resumeWithException(exception: Throwable) { throw exception }

    // Generator implementation
    override suspend fun yield(value: T) {
        setNext(value)
        return CoroutineIntrinsics.suspendCoroutineOrReturn { c ->
            nextStep = c
            CoroutineIntrinsics.SUSPENDED
        }
    }
}
