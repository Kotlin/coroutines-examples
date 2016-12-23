import java.util.concurrent.ForkJoinPool
import kotlin.coroutines.suspendCoroutine

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
    async(Swing) {
        try {
            // suspend while asynchronously making request
            val result = makeRequest()
            // display result in UI, here asyncSwing ensures that we always stay in event dispatch thread
            display(result)
        } catch (exception: Throwable) {
            // process exception
        }
    }
}
