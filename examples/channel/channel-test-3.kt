package channel.test3

import channel.Channel
import channel.go

// https://tour.golang.org/concurrency/3

fun main(args: Array<String>) = go {
    val c = Channel<Int>(2)
    c.send(1)
    c.send(2)
    println(c.receive())
    println(c.receive())
}