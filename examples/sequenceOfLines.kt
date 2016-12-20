import generate.generate
import java.io.BufferedReader
import java.io.FileReader

fun sequenceOfLines(fileName: String) = generate<String> {
    BufferedReader(FileReader(fileName)).use {
        while (true) {
            yield(it.readLine() ?: break)
        }
    }
}

fun main(args: Array<String>) {
    sequenceOfLines("examples/sequenceOfLines.kt")
            .forEach(::println)
}