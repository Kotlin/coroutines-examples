import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Paths

fun main(args: Array<String>) {
    async(Swing) {
        val fileName = "examples/io.kt"
        log("Asynchronously loading file \"$fileName\" ...")
        val channel = AsynchronousFileChannel.open(Paths.get(fileName))
        try {
            val buf = ByteBuffer.allocate(4096)
            val bytesRead = channel.aRead(buf)
            log("Read $bytesRead bytes starting with \"${String(buf.array().copyOf(15))}\"")
        } finally {
            channel.close()
        }
    }
}
