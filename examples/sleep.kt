import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.suspendCoroutine

private val executor = Executors.newSingleThreadScheduledExecutor {
    Thread(it, "sleep-thread").apply { isDaemon = true }
}

suspend fun sleep(millis: Long): Unit = suspendCoroutine { c ->
    executor.schedule({ c.resume(Unit) }, millis, TimeUnit.MILLISECONDS)
}

// test code

fun main(args: Array<String>) {
    async(Swing) {
        log("Let's naively sleep for 1 second")
        sleep(1000L)
        log("We're still in Swing EDT because of the dispatcher!")
    }
}