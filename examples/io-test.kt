import java.io.File

fun main(args: Array<String>) {
    async(Swing) {
        val fileName = "examples/io.kt"
        log("Asynchronously loading file \"$fileName\" ...")
        val bytes = File(fileName).aRead()
        log("Read ${bytes.size} bytes starting with \"${String(bytes.copyOf(15))}\"")
    }
}
