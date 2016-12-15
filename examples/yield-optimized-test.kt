
val fibonacciOptimized: Sequence<Int> = generateOptimized {
    yield(1) // first Fibonacci number
    var cur = 1
    var next = 1
    while (true) {
        yield(next) // next Fibonacci number
        val tmp = cur + next
        cur = next
        next = tmp
    }
}

fun main(args: Array<String>) {
    println(fibonacciOptimized.take(10).joinToString())
}
