package channel.test2a

import channel.Channel
import channel.SendChannel
import channel.go
import channel.suspending
import kotlin.system.measureTimeMillis

suspend fun sum(s: List<Int>, c: SendChannel<Int>) = suspending {
    // simulate long-running CPU-consuming computation
    var sum = 0
    val time = measureTimeMillis {
        kotlin.repeat(100_000_000) {
            for (v in s) {
                sum += v
            }
        }
        c.send(sum)
    }
    println("Sum took $time ms")
}

fun main(args: Array<String>) = go {
    val s = listOf(7, 2, 8, -9, 4, 0)
    val c = Channel<Int>()
    go { sum(s.subList(s.size /2, s.size), c) }
    go { sum(s.subList(0, s.size / 2), c) }
    val time = measureTimeMillis {
        val x = c.receive()
        val y = c.receive()
        println("$x $y ${x + y}")
    }
    println("Main code took $time ms")
}