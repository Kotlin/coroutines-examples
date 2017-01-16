package sequence

import java.io.BufferedReader
import java.io.FileReader

fun sequenceOfLines(fileName: String) = buildSequence<String> {
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