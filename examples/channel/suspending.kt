package channel

import kotlin.coroutines.CoroutineIntrinsics
import kotlin.coroutines.startCoroutine

suspend fun <T> suspending(block: suspend go.() -> T): T = CoroutineIntrinsics.suspendCoroutineOrReturn { c ->
    block.startCoroutine(receiver = go, completion = c)
    CoroutineIntrinsics.SUSPENDED
}
