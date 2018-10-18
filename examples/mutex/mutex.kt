package mutex

import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

class Mutex {
    /*
       Note: this is a non-optimized implementation designed for understandability, so it just
       uses AtomicInteger and ConcurrentLinkedQueue instead of of embedding these data structures right here
       to optimize object count per mutex.
    */

    // -1 == unlocked, >= 0 -> number of active waiters
    private val state = AtomicInteger(-1)
    // can have more waiters than registered in state (we add waiter first)
    private val waiters = ConcurrentLinkedQueue<Waiter>()

    suspend fun lock() {
        // fast path -- try lock uncontended
        if (state.compareAndSet(-1, 0)) return
        // slow path -- other cases
        return suspendCoroutineUninterceptedOrReturn sc@ { uc ->
            // tentatively add a waiter before locking (and we can get resumed because of that!)
            val waiter = Waiter(uc.intercepted())
            waiters.add(waiter)
            loop@ while (true) { // lock-free loop on state
                val curState = state.get()
                if (curState == -1) {
                    if (state.compareAndSet(-1, 0)) {
                        // Locked successfully this time, there were no _other_ waiter.
                        // For simplicity, we don't attempt to unlink the Waiter object from the queue,
                        // but mark ourselves as already resumed in queue (retrieveWaiter will skip marked entries).
                        waiter.resumed = true
                        return@sc Unit // don't suspend, but continue execution with lock

                    }
                } else { // state >= 0 -- already locked --> increase waiters count and sleep peacefully until resumed
                    check(curState >= 0)
                    if (state.compareAndSet(curState, curState + 1)) {
                        break@loop
                    }
                }
            }
            COROUTINE_SUSPENDED // suspend
        }
    }

    fun unlock() {
        while (true) { // lock-free loop on state
            // see if can unlock
            val curState = state.get()
            if (curState == 0) {
                // cannot have any waiters in this state, because we are holding a mutex and only mutex-holder
                // can reduce the number of waiters
                if (state.compareAndSet(0, -1))
                    return // successfully unlocked, no waiters were there to resume
            } else {
                check(curState >= 1)
                // now decrease waiters count and resume waiter
                if (state.compareAndSet(curState, curState - 1)) {
                    // must have a waiter!!
                    retrieveWaiter()!!.c.resume(Unit)
                    return
                }
            }
        }
    }

    private fun retrieveWaiter(): Waiter? {
        while (true) {
            val waiter = waiters.poll() ?: return null
            // see if this is an _actual_ waiter (not a left-over that had actually acquired the lock in the slow path)
            if (!waiter.resumed)
                return waiter
            // otherwise it is an artifact, just look for the next one
        }
    }

    private class Waiter(val c: Continuation<Unit>) {
        var resumed = false
    }
}
