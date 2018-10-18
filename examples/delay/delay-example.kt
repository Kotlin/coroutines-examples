package delay

import context.*
import future.*
import util.*

fun main(args: Array<String>) {
    future(Swing) {
        log("Let's naively sleep for 1 second")
        delay(1000L)
        log("We're still in Swing EDT!")
    }
}