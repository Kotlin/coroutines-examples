package channel

import delay.*
import java.time.*

object Time {
    fun tick(millis: Long): ReceiveChannel<Instant> {
        val c = Channel<Instant>()
        go {
            while (true) {
                delay(millis)
                c.send(Instant.now())
            }
        }
        return c
    }

    fun after(millis: Long): ReceiveChannel<Instant> {
        val c = Channel<Instant>()
        go {
            delay(millis)
            c.send(Instant.now())
            c.close()
        }
        return c
    }
}
