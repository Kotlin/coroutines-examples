package context

import run.*
import util.*
import java.util.concurrent.*
import kotlin.coroutines.*

suspend fun makeRequest(): String {
    log("Making request...")
    return suspendCoroutine { c ->
        ForkJoinPool.commonPool().execute {
            c.resume("Result of the request")
        }
    }
}

fun display(result: String) {
    log("Displaying result '$result'")
}

fun main(args: Array<String>) {
    launch(Swing) {
        try {
            // suspend while asynchronously making request
            val result = makeRequest()
            // display result in UI, here Swing context ensures that we always stay in event dispatch thread
            display(result)
        } catch (exception: Throwable) {
            // process exception
        }
    }
}
