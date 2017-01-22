package context

import future.future
import util.log

fun main(args: Array<String>) {
    future(Swing) {
        log("Let's Swing.delay for 1 second")
        Swing.delay(1000)
        log("We're still in Swing EDT")
    }
}
