package channel

import java.time.Instant

object Time {
    fun tick(millis: Long): ReceiveChannel<Instant> {
        val c = Channel<Instant>()
        go("tick", forever = true) {
            while (true) {
                sleep(millis)
                c.send(Instant.now())
            }
        }
        return c
    }

    fun after(millis: Long): ReceiveChannel<Instant> {
        val c = Channel<Instant>()
        go("after") {
            sleep(millis)
            c.send(Instant.now())
            c.close()
        }
        return c
    }
}
