package suspending

import kotlin.coroutines.CoroutineIntrinsics
import kotlin.coroutines.startCoroutine

suspend fun <T> suspending(block: suspend () -> T): T = CoroutineIntrinsics.suspendCoroutineOrReturn { c ->
    block.startCoroutine(completion = c)
    CoroutineIntrinsics.SUSPENDED
}
