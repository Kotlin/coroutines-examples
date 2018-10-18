package channel.example2

import channel.*

suspend fun sum(s: List<Int>, c: SendChannel<Int>) {
    var sum = 0
    for (v in s) {
        sum += v
    }
    c.send(sum)
}

fun main(args: Array<String>) = mainBlocking {
    val s = listOf(7, 2, 8, -9, 4, 0)
    val c = Channel<Int>()
    go { sum(s.subList(s.size / 2, s.size), c) }
    go { sum(s.subList(0, s.size / 2), c) }
    val x = c.receive()
    val y = c.receive()
    println("$x $y ${x + y}")
}