package async

import coroutines.annotations.coroutine
import coroutines.annotations.operator
import coroutines.annotations.suspend
import coroutines.api.Continuation
import coroutines.api.Coroutine
import java.util.concurrent.CompletableFuture

// TEST CODE

fun foo(): CompletableFuture<String> = CompletableFuture.supplyAsync { "foo" }
fun bar(v: String): CompletableFuture<String> = CompletableFuture.supplyAsync { "bar with $v" }

fun main(args: Array<String>) {
/*
    val future = async {
        println("start")
        val x = await(foo())
        println("got '$x'")
        val y = await(bar(y))
        println("got '$y' after '$x'")
        y
    }
*/
    val future = async({ __anonymous__() })

    future.whenComplete { value, t ->
        println("completed with $value")
    }
    future.join()
}

// LIBRARY CODE
// Note: this code is optimized for readability, the actual implementation would create fewer objects

fun <T> async(@coroutine c: () -> Coroutine<FutureController<T>>): CompletableFuture<T> {
    val controller = FutureController<T>()
    c().entryPoint(controller).resume(Unit)
    return controller.future
}

class FutureController<T> {
    val future = CompletableFuture<T>()

    @suspend fun <V> await(future: CompletableFuture<V>, machine: Continuation<V>) {
        future.whenComplete { value, throwable ->
            if (throwable == null)
                machine.resume(value)
            else
                machine.resumeWithException(throwable)
        }
    }

    @operator fun handleResult(value: T, c: Continuation<Nothing>) {
        future.complete(value)
    }

    @operator fun handleException(t: Throwable, c: Continuation<Nothing>) {
        future.completeExceptionally(t)
    }
}

// GENERATED CODE

class __anonymous__() : Coroutine<FutureController<String>>, Continuation<Any?> {

    private lateinit var controller: FutureController<String>

    override fun entryPoint(controller: FutureController<String>): Continuation<Unit> {
        this.controller = controller
        return this as Continuation<Unit>
    }

    override fun resume(data: Any?) = doResume(data, null)
    override fun resumeWithException(exception: Throwable) = doResume(null, exception)

    /*
        async { v ->
            println("start")
            val x = await(foo())
            println("got '$x'")
            val y = await(bar(y))
            println("got '$y' after '$x'")
            y
        }
     */
    private lateinit var x: String

    private var label = 0
    private fun doResume(data: Any?, exception: Throwable?) {
        try {
            when (label) {
                0 -> {
                    if (exception != null) throw exception
                    data as Unit
                    println("start")
                    label = 1
                    controller.await(foo(), this)
                }
                1 -> {
                    if (exception != null) throw exception
                    x = data as String
                    println("got '$x'")
                    label = 2
                    controller.await(bar(x), this)
                }
                2 -> {
                    if (exception != null) throw exception
                    val y = data as String
                    println("got '$y' after '$x'")
                    label = -1
                    controller.handleResult(y, this)
                }
                else -> throw UnsupportedOperationException("Coroutine $this is in an invalid state")
            }
        } catch(e: Throwable) {
            label = -2
            controller.handleException(e, this)
        }
    }
}