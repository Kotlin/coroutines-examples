package channel.example3

import channel.*

// https://tour.golang.org/concurrency/3

fun main(args: Array<String>) = mainBlocking {
    val c = Channel<Int>(2)
    c.send(1)
    c.send(2)
    println(c.receive())
    println(c.receive())
}