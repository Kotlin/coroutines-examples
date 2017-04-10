package generator

// Samples are inspired by https://developer.mozilla.org/en/docs/Web/JavaScript/Reference/Statements/function*

// Passing arguments into Generators

fun logGenerator() = generate<Unit, String> {
    println("Started with $it")
    println(yield(Unit))
    println(yield(Unit))
    println(yield(Unit))
}

fun main(args: Array<String>) {
    val gen = logGenerator()
    gen.next("start") // start
    gen.next("pretzel") // pretzel
    gen.next("california") // california
    gen.next("mayonnaise") // mayonnaise
}