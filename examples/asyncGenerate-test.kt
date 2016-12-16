import java.util.*

fun main(args: Array<String>) {
    asyncThread("MyThread") {
        // asynchronously generate a number every 500 ms
        val seq = asyncGenerate {
            log("Starting generator")
            for (i in 1..10) {
                log("Generator yields $i")
                yield(i)
                val generatorSleep = 500L
                log("Generator goes to sleep for $generatorSleep ms")
                sleep(generatorSleep)
            }
            log("Generator is done")
        }
        // helper function
        val random = Random()
        // consume asynchronous sequence
        val it = seq.asyncIterator()
        var consumedCounter = 0
        while (true) {
            // I'm feeling lucky the first 5 times; so I don't call hasNext, but do next() immediately (just testing)
            if (consumedCounter++ >= 5) {
                val consumerSleep = random.nextInt(500).toLong()
                log("Consumer goes to sleep for $consumerSleep ms")
                sleep(consumerSleep)
                log("Consumer is checking hasNext()...")
                val hasNext = it.hasNext()
                log("Consumer got hasNext = $hasNext")
                if (!hasNext) break
            }
            val consumerSleep = random.nextInt(500).toLong()
            log("Consumer goes to sleep for $consumerSleep ms")
            sleep(consumerSleep)
            log("Consumer is calling next()...")
            val value = it.next()
            log("Consumer got value = $value")
        }
    }
}