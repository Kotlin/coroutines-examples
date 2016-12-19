package channel.multiplexing

import channel.Channel
import channel.ReceiveChannel
import channel.boring.boring
import channel.go
import channel.suspending

// https://talks.golang.org/2012/concurrency.slide#27

suspend fun fanIn(input1: ReceiveChannel<String>, input2: ReceiveChannel<String>): ReceiveChannel<String> = suspending {
    val c = Channel<String>()
    go {
        for (v in input1)
            c.send(v)
    }
    go {
        for (v in input2)
            c.send(v)
    }
    c // return combo channel
}

fun main(args: Array<String>) = go.main {
    val c = fanIn(boring("Joe"), boring("Ann"))
    for (i in 0..9) {
        println(c.receive())
    }
    println("Your're both boring; I'm leaving.")
}
