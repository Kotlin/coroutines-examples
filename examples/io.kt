import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.suspendCoroutine

suspend fun File.aRead(): ByteArray {
    val channel = AsynchronousFileChannel.open(toPath());
    val tentativeSize = channel.size()
    if (tentativeSize > Int.MAX_VALUE) throw IOException("File is too large to read into byte array")
    val buffer = ByteBuffer.allocate(tentativeSize.toInt())
    return suspendCoroutine { c ->
        channel.read(buffer, 0L, Unit, object : CompletionHandler<Int, Unit> {
            override fun completed(bytesRead: Int, attachment: Unit) {
                val n = bytesRead.coerceAtLeast(0)
                val bytes = if (n == buffer.capacity()) buffer.array()
                    else buffer.array().copyOf(n)
                c.resume(bytes)
            }

            override fun failed(exception: Throwable, attachment: Unit) {
                c.resumeWithException(exception)
            }
        })
    }
}
