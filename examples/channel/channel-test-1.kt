package channel.test1

import channel.go
import channel.suspending

// https://tour.golang.org/concurrency/1

suspend fun say(s: String) = suspending {
    for (i in 0..4) {
        sleep(100)
        println(s)
    }
}

fun main(args: Array<String>) = go {
    go { say("world") }
    say("hello")
}

