package generateActual

val fibonacci: Sequence<Int> = generate {
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

val composite: Sequence<Int> = generate {
    yield(0)
    yieldAll(fibonacci.take(10))
    yieldAll(listOf(-1, -2))
}

fun main(args: Array<String>) {
    println(fibonacci)
    println(fibonacci.take(10).joinToString())
    println(composite.joinToString())
}
