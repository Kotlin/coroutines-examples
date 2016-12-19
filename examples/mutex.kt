package mutex

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineIntrinsics

class Mutex {
    private val locked = AtomicBoolean()
    private val waiters = ConcurrentLinkedQueue<Waiter>()

    suspend fun lock() {
        if (locked.compareAndSet(false, true)) return // locked successfully
        return CoroutineIntrinsics.suspendCoroutineOrReturn { c ->
            val waiter = Waiter(c)
            waiters.add(waiter)
            // try lock again
            if (locked.compareAndSet(false, true)) {
                // locked successfully this time -- try mark as resumed
                if (waiter.resumed.compareAndSet(false, true))
                    return Unit // don't suspend, but continue execution with lock
                // was already resumed by some other thread -> suspend
            }
            CoroutineIntrinsics.SUSPENDED // suspend
        }
    }

    fun unlock() {
        // unlock first, then resume one waiter
        locked.set(false)
        while (true) {
            val waiter = waiters.poll() ?: break
            // try resume one waiter
            if (waiter.resumed.compareAndSet(false, true)) {
                waiter.c.resume(Unit)
                break
            }
        }
    }

    private class Waiter(val c: Continuation<Unit>) {
        val resumed = AtomicBoolean()
    }
}
