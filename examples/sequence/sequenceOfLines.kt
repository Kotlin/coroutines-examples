package sequence

import java.io.*

fun sequenceOfLines(fileName: String) = sequence<String> {
    BufferedReader(FileReader(fileName)).use {
        while (true) {
            yield(it.readLine() ?: break)
        }
    }
}

fun main(args: Array<String>) {
    sequenceOfLines("examples/sequence/sequenceOfLines.kt")
            .forEach(::println)
}