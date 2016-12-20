package channel.test9

import channel.go
import mutex.Mutex
import suspending.suspending

// https://tour.golang.org/concurrency/9

class SafeCounter {
    private val v = mutableMapOf<String, Int>()
    private val mux = Mutex()

    suspend fun inc(key: String) = suspending {
        mux.lock()
        try { v[key] = v.getOrDefault(key, 0) + 1 }
        finally { mux.unlock() }
    }

    suspend fun get(key: String): Int? = suspending {
        mux.lock()
        try { v[key] }
        finally { mux.unlock() }
    }
}

fun main(args: Array<String>) = go.main {
    val c = SafeCounter()
    for (i in 0..999) {
        go { c.inc("somekey") } // 1000 concurrent coroutines
    }
    sleep(1000)
    println("${c.get("somekey")}")
}
