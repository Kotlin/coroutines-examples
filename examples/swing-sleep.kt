import javax.swing.Timer
import kotlin.coroutines.suspendCoroutine

suspend fun Swing.sleep(millis: Int): Unit = suspendCoroutine { c ->
    Timer(millis) { c.resume(Unit) }.apply {
        isRepeats = false
        start()
    }
}

// test code

fun main(args: Array<String>) {
    async(Swing) {
        log("Let's Swing.sleep for 1 second")
        Swing.sleep(1000)
        log("We're still in Swing EDT")
    }
}