package generator

// Samples are inspired by https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Statements/function*

// Example with yieldAll

fun anotherGenerator(i: Int) = generate<Int, Unit> {
    yield(i + 1)
    yield(i + 2)
    yield(i + 3)
}

fun generator(i: Int) = generate<Int, Unit> {
    yield(i)
    yieldAll(anotherGenerator(i), Unit)
    yield(i + 10)
}

fun main(args: Array<String>) {
    val gen = generator(10)
    println(gen.next(Unit)) // 10
    println(gen.next(Unit)) // 11
    println(gen.next(Unit)) // 12
    println(gen.next(Unit)) // 13
    println(gen.next(Unit)) // 20
    println(gen.next(Unit)) // null
}