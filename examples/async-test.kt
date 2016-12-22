import java.util.concurrent.CompletableFuture

// TEST CODE

fun foo(): CompletableFuture<String> = CompletableFuture.supplyAsync { "foo" }
fun bar(v: String): CompletableFuture<String> = CompletableFuture.supplyAsync { "bar with $v" }

fun main(args: Array<String>) {
    val future = async {
        println("start")
        val x = foo().await()
        println("got '$x'")
        val y = bar(x).await()
        println("got '$y' after '$x'")
        y
    }
    future.join()
}

