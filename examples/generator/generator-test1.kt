package generator

// Samples are inspired by https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Statements/function*

// Simple example

fun idMaker() = generate<Int, Unit> {
    var index = 0
    while (index < 3)
        yield(index++)
}

fun main(args: Array<String>) {
    val gen = idMaker()
    println(gen.next(Unit)) // 0
    println(gen.next(Unit)) // 1
    println(gen.next(Unit)) // 2
    println(gen.next(Unit)) // null
}