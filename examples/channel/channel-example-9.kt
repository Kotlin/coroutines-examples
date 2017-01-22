package channel.example9

import channel.go
import channel.mainBlocking
import delay.delay
import mutex.Mutex

// https://tour.golang.org/concurrency/9

class SafeCounter {
    private val v = mutableMapOf<String, Int>()
    private val mux = Mutex()

    suspend fun inc(key: String) {
        mux.lock()
        try { v[key] = v.getOrDefault(key, 0) + 1 }
        finally { mux.unlock() }
    }

    suspend fun get(key: String): Int? {
        mux.lock()
        return try { v[key] }
        finally { mux.unlock() }
    }
}

fun main(args: Array<String>) = mainBlocking {
    val c = SafeCounter()
    for (i in 0..999) {
        go { c.inc("somekey") } // 1000 concurrent coroutines
    }
    delay(1000)
    println("${c.get("somekey")}")
}
