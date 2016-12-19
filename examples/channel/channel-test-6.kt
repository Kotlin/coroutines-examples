package channel.test6

import channel.Time
import channel.go
import channel.whileSelect

// https://tour.golang.org/concurrency/6

fun main(args: Array<String>) = go.main {
    val tick = Time.tick(100)
    val boom = Time.after(500)
    whileSelect {
        tick.onReceive {
            println("tick.")
            true // continue loop
        }
        boom.onReceive {
            println("BOOM!")
            false // break loop
        }
        onDefault {
            println("    .")
            sleep(50)
            true // continue loop
        }
    }
}
