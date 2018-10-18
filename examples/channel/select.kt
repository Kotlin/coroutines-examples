package channel

import kotlin.coroutines.*

suspend inline fun <R> select(block: SelectorBuilder<R>.() -> Unit): R =
    SelectorBuilder<R>().apply { block() }.doSelect()

class SelectorBuilder<R> {
    private val cases = mutableListOf<SelectCase<*, R>>()

    fun <T> SendChannel<T>.onSend(value: T, action: () -> R) {
        cases.add(SendCase(this, value, action))
    }

    fun <T> ReceiveChannel<T>.onReceive(action: (T) -> R) {
        cases.add(ReceiveCase(this, action))
    }

    fun onDefault(action: suspend () -> R) {
        cases.add(DefaultCase(action))
    }

    suspend fun doSelect(): R {
        require(!cases.isEmpty())
        return suspendCoroutine { c ->
            val selector = Selector(c, cases)
            for (case in cases) {
                case.selector = selector
                if (case.select(selector)) break
            }
        }
    }
}

suspend fun whileSelect(block: SelectorBuilder<Boolean>.() -> Unit) {
    while(select(block)) { /*loop*/ }
}

