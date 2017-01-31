package context

import javax.swing.Timer
import kotlin.coroutines.experimental.suspendCoroutine

suspend fun Swing.delay(millis: Int): Unit = suspendCoroutine { cont ->
    Timer(millis) { cont.resume(Unit) }.apply {
        isRepeats = false
        start()
    }
}
