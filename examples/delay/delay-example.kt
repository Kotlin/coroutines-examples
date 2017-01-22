package delay

import context.Swing
import future.future
import util.log

fun main(args: Array<String>) {
    future(Swing) {
        log("Let's naively sleep for 1 second")
        delay(1000L)
        log("We're still in Swing EDT!")
    }
}