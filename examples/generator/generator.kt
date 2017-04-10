package generator

import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

/*
   ES6-style generator that can send values between coroutine and outer code in both ways.

   Note that in ES6-generators the first invocation of `next()` goes not accept a parameter, but
   just starts a coroutine until a subsequent `yield`, so to adopt it for the type-safe interface
   we must declare `next` to be always invoked with a parameter and make our coroutine receive the first
   parameter to `next` when it starts (so it is not lost). We also have to introduce an additional parameter to
   `yieldAll` to start a delegated generator.
*/

interface Generator<out T, in R> {
    fun next(param: R): T? // returns `null` when generator is over
}

@RestrictsSuspension
interface GeneratorBuilder<in T, R> {
    suspend fun yield(value: T): R
    suspend fun yieldAll(generator: Generator<T, R>, param: R)
}

fun <T, R> generate(block: suspend GeneratorBuilder<T, R>.(R) -> Unit): Generator<T, R> {
    val coroutine = GeneratorCoroutine<T, R>()
    val initial = suspend<R> { result -> coroutine.block(result) }
    coroutine.nextStep = { param -> initial.startCoroutine(param, coroutine) }
    return coroutine
}

// helper function to create suspending function
private fun <T> suspend(block: suspend (T) -> Unit): suspend (T) -> Unit = block

// Generator coroutine implementation class
internal class GeneratorCoroutine<T, R>: Generator<T, R>, GeneratorBuilder<T, R>, Continuation<Unit> {
    lateinit var nextStep: (R) -> Unit
    private var lastValue: T? = null
    private var lastException: Throwable? = null

    // Generator<T, R> implementation

    override fun next(param: R): T? {
        nextStep(param)
        lastException?.let { throw it }
        return lastValue
    }

    // GeneratorBuilder<T, R> implementation

    suspend override fun yield(value: T): R = suspendCoroutineOrReturn { cont ->
        lastValue = value
        nextStep = { param -> cont.resume(param) }
        COROUTINE_SUSPENDED
    }

    suspend override fun yieldAll(generator: Generator<T, R>, param: R): Unit = suspendCoroutineOrReturn sc@ { cont ->
        lastValue = generator.next(param)
        if (lastValue == null) return@sc Unit // delegated coroutine does not generate anything -- resume
        nextStep = { param ->
            lastValue = generator.next(param)
            if (lastValue == null) cont.resume(Unit) // resume when delegate is over
        }
        COROUTINE_SUSPENDED
    }

    // Continuation<Unit> implementation

    override val context: CoroutineContext get() = EmptyCoroutineContext
    override fun resume(value: Unit) { lastValue = null }
    override fun resumeWithException(exception: Throwable) { lastException = exception }
}
