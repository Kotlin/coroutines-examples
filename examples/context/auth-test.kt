package context

import run.runBlocking
import kotlin.coroutines.suspendCoroutine

suspend fun secureAwait(): Unit = suspendCoroutine { cont ->
    println("Current user is ${cont.context[AuthUser]?.name}")
    cont.resume(Unit)
}

fun main(args: Array<String>) {
    runBlocking(AuthUser("admin")) {
        secureAwait()
    }
}
