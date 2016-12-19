package channel.test4
import channel.Channel
import channel.SendChannel
import channel.go
import channel.suspending

// https://tour.golang.org/concurrency/4

suspend fun fibonacci(n: Int, c: SendChannel<Int>) = suspending {
    var x = 0
    var y = 1
    for (i in 0..n - 1) {
        c.send(x)
        val next = x + y
        x = y
        y = next
    }
    c.close()
}

fun main(args: Array<String>) = go {
    val c = Channel<Int>(2)
    go { fibonacci(10, c) }
    while (true) {
        val i = c.receiveOrNull() ?: break
        println(i)
    }
}
