package coroutines.`yield`

import coroutines.api.Continuation
import coroutines.api.Coroutine

// TEST CODE

fun main(args: Array<String>) {
/*
    fun gen() = generate {
        println("yield(1)")
        yield(1)
        println("yield(2)")
        yield(2)
        println("done")
    }
*/
    fun gen() = generate(__anonymous__())

    println(gen().joinToString())

    val sequence = gen()
    println(sequence.zip(sequence).joinToString())
}

// LIBRARY CODE

fun <T> generate(c: () -> Coroutine<GeneratorController<T>>): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> {
        val iterator = GeneratorController<T>()
        iterator.setNextStep(c().entryPoint(iterator))
        return iterator
    }
}

class GeneratorController<T>() : AbstractIterator<T>() {
    override fun computeNext() {
        nextStep.resume(Unit)
    }

    fun advance(value: T, c: Continuation<Unit>) {
        setNext(value)
        setNextStep(c)
    }

    fun handleResult() = done()

    private lateinit var nextStep: Continuation<Unit>

    fun setNextStep(step: Continuation<Unit>) {
        this.nextStep = step
    }

    fun yieldValue(value: T, c: Continuation<Unit>) {
        advance(value, c)
    }

    fun handleResult(result: Unit, c: Continuation<Nothing>) {
        handleResult()
    }
}

// GENERATED CODE

/*
    generate {
        println("yield(1)")
        yield(1)
        println("yield(2)")
        yield(2)
        println("done")
    }
 */
class __anonymous__() : Coroutine<GeneratorController<Int>>,
        Continuation<Any?>,
        Function0<Coroutine<GeneratorController<Int>>> {

    override fun resume(data: Any?) = doResume(data, null)

    override fun resumeWithException(exception: Throwable) = doResume(null, exception)

    private var label = 0
    private fun doResume(data: Any?, exception: Throwable?) {
        when (label) {
            0 -> {
                if (exception != null) throw exception
                data as Unit
                println("yield(1)")
                label = 1
                controller.yieldValue(1, this)
            }
            1 -> {
                if (exception != null) throw exception
                data as Unit
                println("yield(2)")
                label = 2
                controller.yieldValue(2, this)
            }
            2 -> {
                if (exception != null) throw exception
                data as Unit
                println("done")
                label = -1
                controller.handleResult(Unit, this)
            }
            else -> throw UnsupportedOperationException("Coroutine $this is in an invalid state")
        }
    }

    val controller: GeneratorController<Int> get() =
    _controller ?: throw UnsupportedOperationException("Coroutine $this should be initialized before use")

    private var _controller: GeneratorController<Int>? = null

    private fun createOrCopy(): __anonymous__ = if (_controller == null) this else __anonymous__()
    override fun invoke(): Coroutine<GeneratorController<Int>> = createOrCopy()
    override fun entryPoint(controller: GeneratorController<Int>): Continuation<Unit> {
        return createOrCopy().apply {
            _controller = controller
        }
    }

    override fun toString(): String {
        return "${__anonymous__::_controller.name} coroutine"
    }
}