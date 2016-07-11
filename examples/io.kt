package io

import coroutines.annotations.coroutine
import coroutines.annotations.operator
import coroutines.annotations.suspend
import coroutines.api.Continuation
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

// TEST CODE

fun main(args: Array<String>) {
    val input = AsynchronousFileChannel.open(Paths.get("README.md"))
    val output = AsynchronousFileChannel.open(Paths.get("out/README.md"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    val buf = ByteBuffer.allocate(1024)
//    val r = noAsync(buf, input, output)
    val r = withAsync(buf, input, output)

    println("Terminating")
    input.close()
    output.close()

    println("Result is $r")
    println("Resulting file:")
    println(File("out/README.md").readText())
}

private fun withAsync(buf: ByteBuffer, input: AsynchronousFileChannel, output: AsynchronousFileChannel): Int {
    /*
        val future = asyncIO {
            println("Started")
            val bytesRead = input.aRead(buf, 0)
            val bytes = ByteArray(bytesRead)
            buf.flip()
            buf.get(bytes)
            println("Read from file ($bytesRead bytes):")
            println(String(bytes))
            val bytesWritten = output.write(ByteBuffer.wrap(bytes), 0)
            println("$bytesWritten bytes written")
            bytesWritten
        }
    */
    val future = asyncIO<Int> { __anonymous__(this, input, output, buf) }

    println("Waiting for completion")

    return future.join()
}

// Version of the same code with callbacks instead of `asyncIO {}, left for comparison`
private fun noAsync(buf: ByteBuffer, input: AsynchronousFileChannel, output: AsynchronousFileChannel): Int {
    println("Started")
    val latch = CountDownLatch(1)
    var exception: Throwable? = null
    var result: Int? = null
    input.read(buf, 0, null,
            object : CompletionHandler<Int, Nothing?> {
                override fun completed(bytesRead: Int, v: Nothing?) {
                    println("Read from file ($bytesRead bytes):")
                    val bytes = ByteArray(bytesRead)
                    buf.flip()
                    buf.get(bytes)
                    println(String(bytes))

                    output.write(ByteBuffer.wrap(bytes), 0, null,
                            object : CompletionHandler<Int, Nothing?> {
                                override fun completed(bytesWritten: Int, attachment: Nothing?) {
                                    println("$bytesWritten bytes written")
                                    result = bytesWritten
                                    latch.countDown()
                                }

                                override fun failed(exc: Throwable, attachment: Nothing?) {
                                    println("Write failed: " + exc.toString())
                                    latch.countDown()
                                }
                            })
                }

                override fun failed(exc: Throwable, v: Nothing?) {
                    println("Read failed: " + exc.toString())
                    latch.countDown()
                }
            })
    println("Waiting for completion")

    latch.await()

    if (exception != null) throw exception
    return result!!
}

// LIBRARY CODE
// Note: this code is optimized for readability, the actual implementation would create fewer objects

fun <T> asyncIO(@coroutine c: AsyncIOController<T>.() -> Continuation<Unit>): CompletableFuture<T> {
    val controller = AsyncIOController<T>()
    controller.c().resume(Unit)
    return controller.future
}

class AsyncIOController<T> {
    val future = CompletableFuture<T>()

    private class AsyncIOHandler(val c: Continuation<Int>) : CompletionHandler<Int, Nothing?> {
        override fun completed(result: Int, attachment: Nothing?) {
            c.resume(result)
        }

        override fun failed(exc: Throwable, attachment: Nothing?) {
            c.resumeWithException(exc)
        }
    }

    @suspend fun AsynchronousFileChannel.aRead(buf: ByteBuffer, position: Long, c: Continuation<Int>) {
        this.read(buf, position, null, AsyncIOHandler(c))
    }

    @suspend fun AsynchronousFileChannel.aWrite(buf: ByteBuffer, position: Long, c: Continuation<Int>) {
        this.write(buf, position, null, AsyncIOHandler(c))
    }

    @operator fun handleResult(value: T, c: Continuation<Nothing>) {
        future.complete(value)
    }

    @operator fun handleException(t: Throwable, c: Continuation<Nothing>) {
        future.completeExceptionally(t)
    }
}

// GENERATED CODE

class __anonymous__(
        val controller: AsyncIOController<Int>,
        val input: AsynchronousFileChannel,
        val output: AsynchronousFileChannel,
        val buf: ByteBuffer
) : Continuation<Any?> {

    override fun resume(data: Any?) = doResume(data, null)
    override fun resumeWithException(exception: Throwable) = doResume(null, exception)

    /*
        asyncIO {
            println("Started")
            val bytesRead = input.aRead(buf, 0)
            val bytes = ByteArray(bytesRead)
            buf.flip()
            buf.get(bytes)
            println("Read from file ($bytesRead bytes):")
            println(String(bytes))
            val bytesWritten = output.write(ByteBuffer.wrap(bytes), 0)
            println("$bytesWritten bytes written")
            bytesWritten
        }.join()
    */

    private var label = 0
    private fun doResume(data: Any?, exception: Throwable?) {
        try {
            when (label) {
                0 -> {
                    if (exception != null) throw exception
                    data as Unit
                    println("Started")
                    label = 1
                    with (controller) {
                        input.aRead(buf, 0, this@__anonymous__ as Continuation<Int>)
                    }
                }
                1 -> {
                    if (exception != null) throw exception
                    val bytesRead = data as Int
                    val bytes = ByteArray(bytesRead)
                    buf.flip()
                    buf.get(bytes)
                    println("Read from file ($bytesRead bytes):")
                    println(String(bytes))
                    val tmp = ByteBuffer.wrap(bytes)
                    label = 2
                    with (controller) {
                        output.aWrite(tmp, 0, this@__anonymous__ as Continuation<Int>)
                    }
                }
                2 -> {
                    if (exception != null) throw exception
                    val bytesWritten = data as Int
                    println("$bytesWritten bytes written")
                    label = -1
                    controller.handleResult(bytesWritten, this)
                }
                else -> throw UnsupportedOperationException("Coroutine $this is in an invalid state")
            }
        } catch(e: Throwable) {
            label = -2
            controller.handleException(e, this)
        }
    }
}