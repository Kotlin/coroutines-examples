import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineIntrinsics
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.suspendCoroutine

interface AsyncGenerator<T> {
    suspend fun yield(value: T)
}

interface AsyncSequence<T> {
    fun asyncIterator(): AsyncIterator<T>
}

interface AsyncIterator<T> {
    suspend fun hasNext(): Boolean
    suspend fun next(): T
}

fun <T> asyncGenerate(block: suspend AsyncGenerator<T>.() -> Unit): AsyncSequence<T> = object : AsyncSequence<T> {
    override fun asyncIterator(): AsyncIterator<T> {
        val iterator = AsyncGeneratorIterator<T>()
        iterator.nextStep = block.createCoroutine(receiver = iterator, completion = iterator)
        return iterator
    }
}

class AsyncGeneratorIterator<T>: AsyncIterator<T>, AsyncGenerator<T>, Continuation<Unit> {
    var computedNext = false
    var nextValue: T? = null
    var nextStep: Continuation<Unit>? = null

    var computesNext = false //
    var computeContinuation: Continuation<*>? = null // cont Boolean or cont T

    suspend fun computeHasNext(): Boolean = suspendCoroutine { c ->
        computesNext = false
        computeContinuation = c
        nextStep!!.resume(Unit) // leaves it in "done" state if crashes
    }

    suspend fun computeNext(): T = suspendCoroutine { c ->
        computesNext = true
        computeContinuation = c
        nextStep!!.resume(Unit) // leaves it in "done" state if crashes
    }

    @Suppress("UNCHECKED_CAST")
    fun resumeIterator(exception: Throwable?) {
        if (exception != null) {
            done()
            computeContinuation!!.resumeWithException(exception)
            return
        }
        if (computesNext) {
            computedNext = false
            (computeContinuation as Continuation<T>).resume(nextValue as T)
        } else {
            (computeContinuation as Continuation<Boolean>).resume(nextStep != null)
        }
    }

    override suspend fun hasNext(): Boolean {
        if (!computedNext) return computeHasNext()
        return nextStep != null
    }

    override suspend fun next(): T {
        if (!computedNext) return computeNext()
        computedNext = false
        return nextValue as T
    }

    private fun done() {
        computedNext = true
        nextStep = null
    }

    // Completion continuation implementation
    override fun resume(value: Unit) {
        done()
        resumeIterator(null)
    }

    override fun resumeWithException(exception: Throwable) {
        done()
        resumeIterator(exception)
    }

    // Generator implementation
    override suspend fun yield(value: T) {
        return CoroutineIntrinsics.suspendCoroutineOrReturn { c ->
            computedNext = true
            nextValue = value
            nextStep = c
            resumeIterator(null)
            CoroutineIntrinsics.SUSPENDED
        }
    }
}
