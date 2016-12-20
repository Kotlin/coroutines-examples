package channel.primes

import channel.Channel
import channel.ReceiveChannel
import channel.SendChannel
import channel.go
import suspending.suspending

// https://talks.golang.org/2012/concurrency.slide#53
// http://golang.org/s/prime-sieve

// Send the sequence 2, 3, 4, ... to channel 'ch'
suspend fun generate(ch: SendChannel<Int>) = suspending {
    var i = 2
    while (true) {
        ch.send(i++)
    }
}

// Copy the values from channel 'in' to channel 'out', removing those divisible by 'prime'
suspend fun filter(inp: ReceiveChannel<Int>, out: SendChannel<Int>, prime: Int) = suspending {
    while (true) {
        val i = inp.receive()
        if (i % prime != 0) {
            out.send(i)
        }
    }
}

fun main(args: Array<String>) = go.main {
    var ch = Channel<Int>() // Create a new channel
    go { generate(ch) } // Launch Generate coroutine
    for (i in 0..9) {
        val prime = ch.receive()
        println(prime)
        val ch1 = Channel<Int>()
        val ch0 = ch // go { ... } currently captures value _by reference_ so need to pass of a copy of variable 'ch'
        go { filter(ch0, ch1, prime) }
        ch = ch1
    }
}

