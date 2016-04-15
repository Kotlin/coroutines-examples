package `yield`

import coroutines.api.*

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
    fun gen() = generate({__anonymous__()})

    println(gen().joinToString())

    val sequence = gen()
    println(sequence.zip(sequence).joinToString())
}

// LIBRARY CODE
// Note: this code is optimized for readability, the actual implementation would create fewer objects

fun <T> generate(@coroutine_ c: () -> Coroutine<GeneratorController<T>>): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> {
        val iterator = GeneratorController<T>()
        iterator.setNextStep(c().entryPoint(iterator))
        return iterator
    }
}

class GeneratorController<T>() : AbstractIterator<T>() {
    private lateinit var nextStep: Continuation<Unit>

    override fun computeNext() {
        nextStep.resume(Unit)
    }

    fun setNextStep(step: Continuation<Unit>) {
        this.nextStep = step
    }


    @suspend fun yieldValue(value: T, c: Continuation<Unit>) {
        setNext(value)
        setNextStep(c)
    }

    @operator fun handleResult(result: Unit, c: Continuation<Nothing>) {
        done()
    }
}

// GENERATED CODE

class __anonymous__() : Coroutine<GeneratorController<Int>>, Continuation<Any?> {
    private lateinit var controller: GeneratorController<Int>

    override fun entryPoint(controller: GeneratorController<Int>): Continuation<Unit> {
        this.controller = controller
        return this as Continuation<Unit>
    }

    override fun resume(data: Any?) = doResume(data, null)
    override fun resumeWithException(exception: Throwable) = doResume(null, exception)

    /*
        generate {
            println("yield(1)")
            yield(1)
            println("yield(2)")
            yield(2)
            println("done")
        }
     */
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
}