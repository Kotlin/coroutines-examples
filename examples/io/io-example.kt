package io

import context.*
import run.*
import util.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*

fun main(args: Array<String>) {
    launch(Swing) {
        val fileName = "examples/io/io.kt"
        log("Asynchronously loading file \"$fileName\" ...")
        val channel = AsynchronousFileChannel.open(Paths.get(fileName))
        try {
            val buf = ByteBuffer.allocate(4096)
            val bytesRead = channel.aRead(buf)
            log("Read $bytesRead bytes starting with \"${String(buf.array().copyOf(10))}\"")
        } finally {
            channel.close()
        }
    }
}
