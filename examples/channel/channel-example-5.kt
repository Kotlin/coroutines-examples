package channel.example5

import channel.*

// https://tour.golang.org/concurrency/5

suspend fun fibonacci(c: SendChannel<Int>, quit: ReceiveChannel<Int>) {
    var x = 0
    var y = 1
    whileSelect {
        c.onSend(x) {
            val next = x + y
            x = y
            y = next
            true // continue while loop
        }
        quit.onReceive {
            println("quit")
            false // break while loop
        }
    }
}

fun main(args: Array<String>) = mainBlocking {
    val c = Channel<Int>(2)
    val quit = Channel<Int>(2)
    go {
        for (i in 0..9)
            println(c.receive())
        quit.send(0)
    }
    fibonacci(c, quit)
}
