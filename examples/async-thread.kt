import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationDispatcher
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

interface AsyncThreadScope {
    fun <T> async(block: suspend AsyncThreadScope.() -> T): CompletableFuture<T>
    suspend fun sleep(time: Long)
}

fun <T> asyncThread(name: String, block: suspend AsyncThreadScope.() -> T): CompletableFuture<T> {
    val scope = AsyncThreadScopeImpl<T>(name)
    block.startCoroutine(receiver = scope, completion = scope, dispatcher = scope)
    scope.thread.start()
    return scope.completionFuture
}

private class Task(val time: Long, val block: () -> Unit) : Comparable<Task> {
    fun run() = block()
    override fun compareTo(other: Task): Int = time.compareTo(other.time)
}

private class AsyncThreadScopeImpl<T>(name: String) : Runnable, AsyncThreadScope, ContinuationDispatcher, Continuation<T> {
    val thread = Thread(this, name)
    val completionFuture = CompletableFuture<T>()
    val lock = ReentrantLock()
    val queue = PriorityQueue<Task>()

    inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    fun schedule(time: Long = 0, block: () -> Unit) {
        locked { queue.add(Task(time, block)) }
        unpark()
    }

    override fun run() {
        while (!completionFuture.isDone) {
            val currentTime = System.currentTimeMillis()
            val task = locked {
                val task: Task? = queue.peek()
                if (task != null && task.time <= currentTime) queue.remove()
                task
            }
            if (task == null) {
                LockSupport.park()
                continue
            }
            if (task.time > currentTime) {
                LockSupport.parkNanos(TimeUnit.NANOSECONDS.toMicros(task.time - currentTime))
                continue
            }
            task.run()
        }
    }

    // scope
    override fun <T> async(block: suspend AsyncThreadScope.() -> T): CompletableFuture<T> {
        checkThread()
        val f = CompletableFuture<T>()
        block.startCoroutine(receiver = this, completion = object : Continuation<T> {
            override fun resume(value: T) {
                f.complete(value)
            }

            override fun resumeWithException(exception: Throwable) {
                f.completeExceptionally(exception)
            }
        }, dispatcher = this)
        return f
    }

    suspend override fun sleep(time: Long): Unit = suspendCoroutine { c ->
        schedule(System.currentTimeMillis() + time) {
            c.resume(Unit)
        }
    }

    // dispatcher
    override fun <T> dispatchResume(value: T, continuation: Continuation<T>): Boolean {
        if (Thread.currentThread() == thread) return false
        schedule { continuation.resume(value) }
        return true
    }

    override fun dispatchResumeWithException(exception: Throwable, continuation: Continuation<*>): Boolean {
        if (Thread.currentThread() == thread) return false
        schedule { continuation.resumeWithException(exception) }
        return true
    }

    // completion
    override fun resume(value: T) {
        checkThread()
        completionFuture.complete(value)
        unpark()
    }

    override fun resumeWithException(exception: Throwable) {
        checkThread()
        completionFuture.completeExceptionally(exception)
        unpark()
    }

    fun unpark() {
        LockSupport.unpark(thread)
    }

    fun checkThread() {
        check(Thread.currentThread() == thread) { "Should not escape from $thread" }
    }
}

// test code

fun main(args: Array<String>) {
    log("Starting MyEventThread")
    val f = asyncThread("MyEventThread") {
        log("Hello, world!")
        val f1 = async {
            log("f1 is sleeping")
            sleep(1000) // sleep 1s
            log("f1 returns 1")
            1
        }
        val f2 = async {
            log("f2 is sleeping")
            sleep(1000) // sleep 1s
            log("f2 returns 2")
            2
        }
        log("I'll wait for both f1 and f2. It should take just a second!")
        val sum = f1.await() + f2.await()
        log("And the sum is $sum")
    }
    f.get()
    log("Terminated")
}